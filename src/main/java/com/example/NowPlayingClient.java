package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

public class NowPlayingClient implements ClientModInitializer, ModMenuApi {
    private static Process csharpProcess;
    public static NowPlayingConfig config;

    private static volatile String cachedMediaTitle = null;
    private static volatile String cachedArtistName = null;
    private static volatile boolean cachedIsSpotify = false;
    private static volatile NativeImage cachedImage = null;

    private static volatile String mediaTitleText = "Loading Now Playing...";
    private static volatile String artistNameText = "Loading Artist...";
    private static final Identifier nowPlayingImageId = Identifier.of("nowplaying", "media");
    private static volatile boolean imageLoaded = false;
    private static volatile boolean currentIsSpotify = false;

    private static volatile double currentProgress = 0.0;
    private static volatile double targetProgress = 0.0;
    private static volatile double currentPositionSec = 0.0;
    private static volatile double targetPositionSec = 0.0;
    private static volatile double currentEndSec = 0.0;
    private static volatile double targetEndSec = 0.0;

    private static volatile long lastUpdateTime = System.nanoTime();
    private static final double PROGRESS_SMOOTHING_FACTOR = 0.15;
    private static final double TIME_SMOOTHING_FACTOR = 0.1;

    private static volatile boolean isMediaActive = false;
    private static volatile boolean isPlaying = false;

    @Override
    @Environment(EnvType.CLIENT)
    public void onInitializeClient() {
        AutoConfig.register(NowPlayingConfig.class, JanksonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(NowPlayingConfig.class).getConfig();

        GuiRegistry registry = AutoConfig.getGuiRegistry(NowPlayingConfig.class);

        registry.registerPredicateProvider(
                (i18n, field, config, defaults, guiRegistry) -> new CustomSideGuiProvider().get(i18n, field, config, defaults, guiRegistry),
                field -> field.getType().equals(NowPlayingConfig.Side.class)
        );

        if (!NowPlayingFileManager.ensureExecutableReady()) {
            System.err.println("[NowPlayingMod] Failed to prepare C# executable. Mod functionality might be limited.");

            isMediaActive = false;
            mediaTitleText = "Error: C# server not found/extracted.";
            artistNameText = "Check logs for details.";

            return;
        }

        launchCSharpScript();

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            System.out.println("[NowPlayingMod] Minecraft server stopped event detected. Attempting to stop C# server.");
            stopCSharpScript();
        });

        CompletableFuture.runAsync(() -> {
            while (true) {
                if (csharpProcess != null && csharpProcess.isAlive()) {
                    // Get media info
                    try {
                        URL url = new URL("http://localhost:58888/media_info");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(1000);
                        conn.setReadTimeout(1000);

                        int status = conn.getResponseCode();
                        if (status == 200) {
                            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder content = new StringBuilder();
                            String line;
                            while ((line = in.readLine()) != null) {
                                content.append(line);
                            }
                            in.close();

                            String json = content.toString();

                            String newMediaTitle = ellipsizeText(extractJsonValue(json, "title"));
                            String newArtistName = ellipsizeText(extractJsonValue(json, "artist"));
                            String newRawPosition = extractJsonValue(json, "position");
                            String newRawStart = extractJsonValue(json, "start");
                            String newRawEnd = extractJsonValue(json, "end");
                            String newAppName = extractJsonValue(json, "app");
                            boolean newIsSpotify = newAppName.toLowerCase().contains("spotify");
                            String newStatus = extractJsonValue(json, "status");

                            // Set the isPlaying field based on the status from the C# server
                            isPlaying = newStatus.equals("Playing");

                            boolean textChanged = !newMediaTitle.equals(cachedMediaTitle) ||
                                    !newArtistName.equals(cachedArtistName);

                            boolean isActiveStatus = newStatus.equals("Playing") || newStatus.equals("Paused");

                            isMediaActive = isActiveStatus && !newMediaTitle.isEmpty() && !newMediaTitle.equals("(none)") && !newMediaTitle.equals("(unknown)");

                            if (textChanged) {
                                mediaTitleText = newMediaTitle;
                                artistNameText = newArtistName;
                                cachedMediaTitle = newMediaTitle;
                                cachedArtistName = newArtistName;
                            }

                            boolean appTypeChanged = newIsSpotify != cachedIsSpotify;
                            if (appTypeChanged) {
                                currentIsSpotify = newIsSpotify;
                                cachedIsSpotify = newIsSpotify;
                            }

                            double parsedPositionSec = parseTimeToSeconds(newRawPosition);
                            double parsedStartSec = parseTimeToSeconds(newRawStart);
                            double parsedEndSec = parseTimeToSeconds(newRawEnd);

                            targetPositionSec = parsedPositionSec;
                            targetEndSec = parsedEndSec;

                            if (parsedEndSec > parsedStartSec) {
                                targetProgress = (parsedPositionSec - parsedStartSec) / (parsedEndSec - parsedStartSec);
                                targetProgress = Math.max(0, Math.min(targetProgress, 1));
                            } else {
                                targetProgress = 0.0;
                                targetPositionSec = 0.0;
                                targetEndSec = 0.0;
                            }

                            // Get media image
                            try {
                                URL imgUrl = new URL("http://localhost:58888/media_image.jpg");
                                HttpURLConnection imgConn = (HttpURLConnection) imgUrl.openConnection();
                                imgConn.setConnectTimeout(1000);
                                imgConn.setReadTimeout(1000);

                                if (imgConn.getResponseCode() == 200) {
                                    try (InputStream input = imgConn.getInputStream()) {
                                        NativeImage fetchedImage = NativeImage.read(input);

                                        boolean imageNeedsUpdate = isImageNeedsUpdate(fetchedImage, textChanged, appTypeChanged);

                                        if (imageNeedsUpdate) {
                                            NativeImage imageToRegister = fetchedImage;
                                            final NativeImage finalImageToRegister = imageToRegister;
                                            MinecraftClient.getInstance().execute(() -> {
                                                try {
                                                    MinecraftClient.getInstance().getTextureManager().registerTexture(
                                                            nowPlayingImageId,
                                                            new NativeImageBackedTexture(finalImageToRegister)
                                                    );
                                                    imageLoaded = true;
                                                } catch (Exception e) {
                                                    imageLoaded = false;
                                                    finalImageToRegister.close();
                                                    if (cachedImage == finalImageToRegister)
                                                        cachedImage = null;
                                                }
                                            });
                                        } else {
                                            fetchedImage.close();
                                        }
                                    }
                                } else {
                                    imageLoaded = false;
                                    if (cachedImage != null) {
                                        cachedImage.close();
                                        cachedImage = null;
                                    }
                                    MinecraftClient.getInstance().execute(() -> {
                                        MinecraftClient.getInstance().getTextureManager().destroyTexture(nowPlayingImageId);
                                    });
                                }
                                imgConn.disconnect();
                            } catch (Exception ex) {
                                if (imageLoaded) {
                                    System.err.println("Exception fetching media image: " + ex.getMessage());
                                }
                                imageLoaded = false;
                                if (cachedImage != null) {
                                    cachedImage.close();
                                    cachedImage = null;
                                }
                                MinecraftClient.getInstance().execute(() -> {
                                    MinecraftClient.getInstance().getTextureManager().destroyTexture(nowPlayingImageId);
                                });
                            }

                        } else {
                            mediaTitleText = "";
                            artistNameText = "";
                            imageLoaded = false;
                            cachedMediaTitle = "";
                            cachedArtistName = "";
                            cachedIsSpotify = false;
                            targetProgress = 0.0;
                            currentProgress = 0.0;
                            targetPositionSec = 0.0;
                            targetEndSec = 0.0;
                            isPlaying = false;
                            if (cachedImage != null) {
                                cachedImage.close();
                                cachedImage = null;
                            }
                            MinecraftClient.getInstance().execute(() -> {
                                MinecraftClient.getInstance().getTextureManager().destroyTexture(nowPlayingImageId);
                            });
                        }

                        conn.disconnect();
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        mediaTitleText = "An error occurred.";
                        artistNameText = "";
                        imageLoaded = false;
                        cachedMediaTitle = null;
                        cachedArtistName = null;
                        cachedIsSpotify = false;
                        targetProgress = 0.0;
                        currentProgress = 0.0;
                        targetPositionSec = 0.0;
                        targetEndSec = 0.0;
                        isPlaying = false;
                        if (cachedImage != null) {
                            cachedImage.close();
                            cachedImage = null;
                        }
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient.getInstance().getTextureManager().destroyTexture(nowPlayingImageId);
                        });
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                } else {
                    System.out.println("[NowPlayingMod] C# server not running. Waiting for next launch attempt.");
                    isMediaActive = false;
                    mediaTitleText = "C# Server Not Running";
                    artistNameText = "Please check logs.";
                    isPlaying = false;
                    try {
                        Thread.sleep(2000);
                        launchCSharpScript();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || !isMediaActive) {
                return;
            }

            // Update progress and position
            long now = System.nanoTime();
            double frameDeltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            double smoothFactor = frameDeltaTime * 60;

            currentProgress += (targetProgress - currentProgress) * PROGRESS_SMOOTHING_FACTOR * smoothFactor;
            currentProgress = Math.max(0, Math.min(currentProgress, 1));

            if (isDisplayableMedia()) {
                currentPositionSec += frameDeltaTime;
                currentPositionSec += (targetPositionSec - currentPositionSec) * TIME_SMOOTHING_FACTOR * smoothFactor;
                currentPositionSec = Math.min(currentPositionSec, targetEndSec);
                currentEndSec += (targetEndSec - currentEndSec) * TIME_SMOOTHING_FACTOR * smoothFactor;
            } else {
                currentPositionSec += (0.0 - currentPositionSec) * TIME_SMOOTHING_FACTOR * smoothFactor;
                currentEndSec += (0.0 - currentEndSec) * TIME_SMOOTHING_FACTOR * smoothFactor;
            }

            int textPadding = 6;
            int lineHeight = 10;
            int imageWidth = 32;
            int imageHeight = 32;
            int barHeight = 2;
            int barPadding = 2;
            int imageTextSpacing = 10;
            int timelineGap = 4;
            int minTimelineWidth = 80;

            // Prepare text and sizing
            Text mediaTitle = Text.literal(mediaTitleText);
            Text artistName = Text.literal(artistNameText);
            int mediaTitleWidth = client.textRenderer.getWidth(mediaTitle);
            int artistNameWidth = client.textRenderer.getWidth(artistName);

            int textBlockHeight = 0;
            if (config.showMediaTitle) {
                textBlockHeight += lineHeight;
            }
            if (config.showArtistName) {
                textBlockHeight += lineHeight;
            }

            int contentHeight = 0;
            if (textBlockHeight > 0) {
                contentHeight += textBlockHeight;
            }
            if (config.showTimeline) {
                if (textBlockHeight > 0) {
                    contentHeight += timelineGap;
                }
                contentHeight += barHeight + barPadding + lineHeight;
            }

            int panelHeight = 0;
            if (imageLoaded && config.showCoverArt) {
                panelHeight = imageHeight;
            }
            panelHeight = Math.max(panelHeight, contentHeight) + (textPadding * 2);

            int textBlockWidth = 0;
            if (config.showMediaTitle) {
                textBlockWidth = Math.max(textBlockWidth, mediaTitleWidth);
            }
            if (config.showArtistName) {
                textBlockWidth = Math.max(textBlockWidth, artistNameWidth);
            }

            if (config.showTimeline && textBlockWidth < minTimelineWidth) {
                textBlockWidth = minTimelineWidth;
            }

            int panelWidth = 0;
            if (textBlockWidth > 0) {
                panelWidth = textBlockWidth;
            }
            if (imageLoaded && config.showCoverArt) {
                panelWidth += imageWidth;
                if (textBlockWidth > 0) {
                    panelWidth += imageTextSpacing;
                }
            }
            panelWidth += (textPadding * 2);

            if (panelWidth < 100 && !(imageLoaded && config.showCoverArt && textBlockWidth == 0)) {
                panelWidth = 100;
            } else if (textBlockWidth == 0 && imageLoaded && config.showCoverArt) {
                panelWidth = imageWidth + (textPadding * 2);
            }

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int panelX;
            if (config.sidePosition == NowPlayingConfig.Side.LEFT) {
                panelX = 0;
            } else {
                panelX = screenWidth - panelWidth;
            }

            int panelY = (int) ((screenHeight - panelHeight) * (config.yPosition / 100.0));

            // Draw background
            if (shouldDrawPanel()) {
                int bgColor = (int) (config.backgroundOpacity * 2.55) << 24;
                drawContext.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, bgColor);
            }

            int imageStartX = panelX + textPadding;
            int textStartX = panelX + textPadding;
            if (imageLoaded && config.showCoverArt) {
                textStartX += imageWidth + imageTextSpacing;
            }

            int currentY = panelY + textPadding;

            // Draw image
            if (imageLoaded && config.showCoverArt) {
                if (textBlockHeight == 0 && !config.showTimeline) {
                    currentY = panelY + (panelHeight - imageHeight) / 2;
                }
                drawContext.drawTexture(nowPlayingImageId, imageStartX, currentY, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
            }

            // Determine starting Y for text
            int textStartY = panelY + textPadding;
            if (imageLoaded && config.showCoverArt && (textBlockHeight > 0 || config.showTimeline)) {
                int totalContentHeight = 0;
                if (config.showMediaTitle) {
                    totalContentHeight += lineHeight;
                }
                if (config.showArtistName) {
                    totalContentHeight += lineHeight;
                }
                if (config.showTimeline) {
                    if (config.showMediaTitle || config.showArtistName) {
                        totalContentHeight += timelineGap;
                    }
                    totalContentHeight += barHeight + barPadding + lineHeight;
                }
                textStartY = panelY + (panelHeight - totalContentHeight) / 2;
            }
            currentY = textStartY;

            // Draw text
            if (config.showMediaTitle) {
                drawContext.drawTextWithShadow(client.textRenderer, mediaTitle, textStartX, currentY, 0xFFFFFF);
                currentY += lineHeight;
            }
            if (config.showArtistName) {
                drawContext.drawTextWithShadow(client.textRenderer, artistName, textStartX, currentY, 0xAAAAAA);
                currentY += lineHeight;
            }

            // Draw timeline bar
            if (config.showTimeline && targetEndSec > 0) {
                if (config.showMediaTitle || config.showArtistName) {
                    currentY += timelineGap;
                }

                int barY = currentY;

                int barX = textStartX;
                int barWidth;

                if (config.showPlayStatusIcon) {
                    String playPauseSymbol = isPlaying ? "▶" : "❚❚";
                    int iconWidth = client.textRenderer.getWidth(playPauseSymbol);
                    barX += iconWidth + 4;
                    drawContext.drawTextWithShadow(client.textRenderer, Text.literal(playPauseSymbol), textStartX, barY, 0xFFFFFF);
                }

                barWidth = (panelX + panelWidth - textPadding) - barX;

                drawContext.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);
                drawContext.fill(barX, barY, barX + (int) (barWidth * currentProgress), barY + barHeight, 0xFFD3D3D3);

                currentY += barHeight + barPadding;

                // Draw time stamps
                String currentPosString = parseSecondsToTimeStamp(currentPositionSec);
                String endPosString = parseSecondsToTimeStamp(currentEndSec);

                drawContext.drawTextWithShadow(client.textRenderer, Text.literal(currentPosString), barX, currentY, 0xAAAAAA);

                int endPosWidth = client.textRenderer.getWidth(endPosString);
                int endPosTextX = barX + barWidth - endPosWidth;

                drawContext.drawTextWithShadow(client.textRenderer, Text.literal(endPosString), endPosTextX, currentY, 0xAAAAAA);
            }
        });
    }

    private boolean isDisplayableMedia() {
        return mediaTitleText != null && !mediaTitleText.isEmpty() && !mediaTitleText.equals("Loading Now Playing...") && targetProgress > 0 && targetEndSec > 0.0;
    }

    private boolean shouldDrawPanel() {
        return config.showArtistName || config.showTimeline || config.showCoverArt || config.showMediaTitle || config.showPlayStatusIcon;
    }

    private static boolean isImageNeedsUpdate(NativeImage fetchedImage, boolean textChanged, boolean appTypeChanged) {
        boolean imageNeedsUpdate = false;
        if (cachedImage == null) {
            imageNeedsUpdate = true;
        } else if (fetchedImage.getWidth() != cachedImage.getWidth() ||
                fetchedImage.getHeight() != cachedImage.getHeight()) {
            imageNeedsUpdate = true;
        } else {
            if (!imageLoaded || textChanged || appTypeChanged) {
                imageNeedsUpdate = true;
            }
        }
        return imageNeedsUpdate;
    }

    private static String parseSecondsToTimeStamp(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) {
            return "0:00";
        }

        int totalSeconds = (int) Math.round(seconds);

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int remainingSeconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, remainingSeconds);
        } else {
            return String.format("%d:%02d", minutes, remainingSeconds);
        }
    }

    private static double parseTimeToSeconds(String time) {
        if (time == null || time.isEmpty() || time.equals("(unknown)")) {
            return 0;
        }
        try {
            String[] parts = time.split(":");
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                return hours * 3600 + minutes * 60 + seconds;
            } else if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                double seconds = Double.parseDouble(parts[1]);
                return minutes * 60 + seconds;
            }
            return 0;
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse time string: " + time + " - " + e.getMessage());
            return 0;
        } catch (Exception e) {
            System.err.println("Generic error parsing time string: " + time + " - " + e.getMessage());
            return 0;
        }
    }

    private static String ellipsizeText(String text) {
        if (text == null) return "";
        try {
            if (text.length() > 25)
                return text.substring(0, 25) + "...";
            return text;
        } catch (Exception e) {
            return text;
        }
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) {
            search = "\"" + key + "\":";
            start = json.indexOf(search);
            if (start != -1) {
                start += search.length();
                int end = json.indexOf(",", start);
                if (end == -1) {
                    end = json.indexOf("}", start);
                }
                if (end != -1) {
                    String valuePart = json.substring(start, end).trim();
                    if (valuePart.equals("null")) {
                        return "";
                    }
                }
            }
            return "(unknown)";
        }
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "(unknown)";
        return json.substring(start, end);
    }

    private static void launchCSharpScript() {
        if (csharpProcess != null && csharpProcess.isAlive()) {
            System.out.println("[NowPlayingMod] C# server is already running.");
            return;
        }

        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path modConfigDir = configDir.resolve("nowplaying");
            Path csharpExeFolder = modConfigDir.resolve("nowPlayingServer");
            File csharpExeFile = csharpExeFolder.resolve("ConsoleApp6.exe").toFile();

            if (!csharpExeFile.exists()) {
                System.err.println("[NowPlayingMod ERROR] C# server executable not found at: " + csharpExeFile.getAbsolutePath());
                System.err.println("[NowPlayingMod INFO] Please ensure the 'MediaInfoServer' folder from Visual Studio publish is located inside: " + modConfigDir.toAbsolutePath());
                return;
            }

            ProcessBuilder processBuilder = new ProcessBuilder(csharpExeFile.getAbsolutePath());

            processBuilder.directory(csharpExeFile.getParentFile());

            processBuilder.redirectErrorStream(true);

            csharpProcess = processBuilder.start();
            System.out.println("[NowPlayingMod] C# server launched successfully. PID: " + csharpProcess.pid());

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(csharpProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[C# Server Output]: " + line);
                    }
                } catch (IOException e) {
                    System.err.println("[NowPlayingMod ERROR] Error reading C# server process output: " + e.getMessage());
                } finally {
                    System.out.println("[NowPlayingMod] C# server output stream closed.");
                }
            }, "C# Server Output Reader").start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stopCSharpScript();
            }, "C# Server Shutdown Hook"));

        } catch (IOException e) {
            System.err.println("[NowPlayingMod ERROR] Failed to launch C# server: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[NowPlayingMod ERROR] An unexpected error occurred while launching C# server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void stopCSharpScript() {
        if (csharpProcess != null && csharpProcess.isAlive()) {
            System.out.println("[NowPlayingMod] Attempting to stop C# server...");
            csharpProcess.destroy();
            try {
                boolean terminated = csharpProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (terminated) {
                    System.out.println("[NowPlayingMod] C# server stopped successfully.");
                } else {
                    csharpProcess.destroyForcibly();
                    System.out.println("[NowPlayingMod] C# server forcibly stopped.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[NowPlayingMod ERROR] Interrupted while waiting for C# server to stop: " + e.getMessage());
                csharpProcess.destroyForcibly();
            } finally {
                csharpProcess = null;
            }
        } else {
            System.out.println("[NowPlayingMod] C# server is not running or already stopped.");
        }
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(NowPlayingConfig.class, parent).get();
    }
}