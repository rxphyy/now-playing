package com.example;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class NowPlayingFileManager {

    private static final String EXECUTABLE_RESOURCE_PREFIX = "assets/nowplaying/executables/nowPlayingServer/";
    private static final Path TARGET_EXECUTABLE_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("nowplaying")
            .resolve("nowPlayingServer");

    private static final int EXECUTABLE_VERSION = 1;
    private static final Path VERSION_FILE_PATH = TARGET_EXECUTABLE_PATH.resolve("version.txt");

    public static boolean ensureExecutableReady() {
        if (!Files.exists(TARGET_EXECUTABLE_PATH)) {
            try {
                Files.createDirectories(TARGET_EXECUTABLE_PATH);
                System.out.println("[NowPlayingMod] Created directory: " + TARGET_EXECUTABLE_PATH);
            } catch (IOException e) {
                System.err.println("[NowPlayingMod ERROR] Failed to create directory " + TARGET_EXECUTABLE_PATH + ": " + e.getMessage());
                return false;
            }
        }

        int installedVersion = getInstalledExecutableVersion();

        if (installedVersion < EXECUTABLE_VERSION) {
            System.out.println("[NowPlayingMod] Executable version mismatch. Installed: " + installedVersion + ", Required: " + EXECUTABLE_VERSION);
            System.out.println("[NowPlayingMod] Extracting C# server executable to: " + TARGET_EXECUTABLE_PATH);

            try {
                deleteDirectoryContents(TARGET_EXECUTABLE_PATH);

                Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer("nowplaying");

                if (modContainer.isPresent()) {
                    Path modRootPath = modContainer.get().getRootPath();

                    if (Files.isRegularFile(modRootPath)) { // Running from JAR
                        System.out.println("[NowPlayingMod] Running from JAR. Extracting from: " + modRootPath);
                        extractFromJar(modRootPath, EXECUTABLE_RESOURCE_PREFIX, TARGET_EXECUTABLE_PATH);
                    } else if (Files.isDirectory(modRootPath)) { // Running from development environment
                        System.out.println("[NowPlayingMod] Running from development environment. Iterating resources.");
                        extractResourcesFromDev(EXECUTABLE_RESOURCE_PREFIX, TARGET_EXECUTABLE_PATH);
                    } else {
                        System.err.println("[NowPlayingMod ERROR] Mod root path is neither a JAR nor a directory: " + modRootPath);
                        return false;
                    }
                } else {
                    System.err.println("[NowPlayingMod ERROR] Could not find mod container for 'nowplaying'. Cannot extract executable.");
                    return false;
                }

                writeExecutableVersion(EXECUTABLE_VERSION);
                System.out.println("[NowPlayingMod] C# server executable extraction complete.");
                return true;

            } catch (IOException e) {
                System.err.println("[NowPlayingMod ERROR] Failed to extract C# server executable: " + e.getMessage());
                e.printStackTrace();
                return false;
            } catch (Exception e) {
                System.err.println("[NowPlayingMod ERROR] An unexpected error occurred during extraction: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            System.out.println("[NowPlayingMod] C# server executable is already up-to-date (Version: " + installedVersion + ").");
            return true;
        }
    }

    private static int getInstalledExecutableVersion() {
        if (Files.exists(VERSION_FILE_PATH)) {
            try {
                String versionString = Files.readString(VERSION_FILE_PATH).trim();
                return Integer.parseInt(versionString);
            } catch (IOException | NumberFormatException e) {
                System.err.println("[NowPlayingMod WARNING] Could not read or parse executable version file. Assuming 0. " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    private static void writeExecutableVersion(int version) {
        try {
            Files.writeString(VERSION_FILE_PATH, String.valueOf(version));
        } catch (IOException e) {
            System.err.println("[NowPlayingMod ERROR] Failed to write executable version file: " + e.getMessage());
        }
    }

    private static void deleteDirectoryContents(Path directory) throws IOException {
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            try (var stream = Files.walk(directory)) {
                stream.filter(p -> !p.equals(directory))
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    private static void extractFromJar(Path jarFilePath, String resourcePrefix, Path targetDirectory) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith(resourcePrefix) && !entry.isDirectory()) {
                    String relativePath = name.substring(resourcePrefix.length());
                    Path targetFile = targetDirectory.resolve(relativePath);

                    Files.createDirectories(targetFile.getParent());

                    try (InputStream is = jarFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    System.out.println("[NowPlayingMod] Extracted JAR entry: " + relativePath);
                }
            }
        }
    }

    private static void extractResourcesFromDev(String resourcePrefix, Path targetDirectory) throws IOException {
        String[] csharpFiles = {
                "clretwrc.dll",
                "clrgc.dll",
                "clrjit.dll",
                "ConsoleApp6.deps.json",
                "ConsoleApp6.dll",
                "ConsoleApp6.exe",
                "ConsoleApp6.pdb",
                "ConsoleApp6.runtimeconfig.json",
                "coreclr.dll",
                "createdump.exe",
                "hostfxr.dll",
                "hostpolicy.dll",
                "Microsoft.CSharp.dll",
                "Microsoft.DiaSymReader.Native.amd64.dll",
                "Microsoft.VisualBasic.Core.dll",
                "Microsoft.VisualBasic.dll",
                "Microsoft.Win32.Primitives.dll",
                "Microsoft.Win32.Registry.dll",
                "Microsoft.Windows.SDK.NET.dll",
                "mscordaccore.dll",
                "mscordaccore_amd64_amd64_8.0.324.11423.dll",
                "mscordbi.dll",
                "mscorlib.dll",
                "mscorrc.dll",
                "msquic.dll",
                "netstandard.dll",
                "System.AppContext.dll",
                "System.Buffers.dll",
                "System.Collections.Concurrent.dll",
                "System.Collections.dll",
                "System.Collections.Immutable.dll",
                "System.Collections.NonGeneric.dll",
                "System.Collections.Specialized.dll",
                "System.ComponentModel.Annotations.dll",
                "System.ComponentModel.DataAnnotations.dll",
                "System.ComponentModel.dll",
                "System.ComponentModel.EventBasedAsync.dll",
                "System.ComponentModel.Primitives.dll",
                "System.ComponentModel.TypeConverter.dll",
                "System.Configuration.dll",
                "System.Console.dll",
                "System.Core.dll",
                "System.Data.Common.dll",
                "System.Data.DataSetExtensions.dll",
                "System.Data.dll",
                "System.Diagnostics.Contracts.dll",
                "System.Diagnostics.Debug.dll",
                "System.Diagnostics.DiagnosticSource.dll",
                "System.Diagnostics.FileVersionInfo.dll",
                "System.Diagnostics.Process.dll",
                "System.Diagnostics.StackTrace.dll",
                "System.Diagnostics.TextWriterTraceListener.dll",
                "System.Diagnostics.Tools.dll",
                "System.Diagnostics.TraceSource.dll",
                "System.Diagnostics.Tracing.dll",
                "System.dll",
                "System.Drawing.dll",
                "System.Drawing.Primitives.dll",
                "System.Dynamic.Runtime.dll",
                "System.Formats.Asn1.dll",
                "System.Formats.Tar.dll",
                "System.Globalization.Calendars.dll",
                "System.Globalization.dll",
                "System.Globalization.Extensions.dll",
                "System.IO.Compression.Brotli.dll",
                "System.IO.Compression.dll",
                "System.IO.Compression.FileSystem.dll",
                "System.IO.Compression.Native.dll",
                "System.IO.Compression.ZipFile.dll",
                "System.IO.dll",
                "System.IO.FileSystem.AccessControl.dll",
                "System.IO.FileSystem.dll",
                "System.IO.FileSystem.DriveInfo.dll",
                "System.IO.FileSystem.Primitives.dll",
                "System.IO.FileSystem.Watcher.dll",
                "System.IO.IsolatedStorage.dll",
                "System.IO.MemoryMappedFiles.dll",
                "System.IO.Pipes.AccessControl.dll",
                "System.IO.Pipes.dll",
                "System.IO.UnmanagedMemoryStream.dll",
                "System.Linq.dll",
                "System.Linq.Expressions.dll",
                "System.Linq.Parallel.dll",
                "System.Linq.Queryable.dll",
                "System.Memory.dll",
                "System.Net.dll",
                "System.Net.Http.dll",
                "System.Net.Http.Json.dll",
                "System.Net.HttpListener.dll",
                "System.Net.Mail.dll",
                "System.Net.NameResolution.dll",
                "System.Net.NetworkInformation.dll",
                "System.Net.Ping.dll",
                "System.Net.Primitives.dll",
                "System.Net.Quic.dll",
                "System.Net.Requests.dll",
                "System.Net.Security.dll",
                "System.Net.ServicePoint.dll",
                "System.Net.Sockets.dll",
                "System.Net.WebClient.dll",
                "System.Net.WebHeaderCollection.dll",
                "System.Net.WebProxy.dll",
                "System.Net.WebSockets.Client.dll",
                "System.Net.WebSockets.dll",
                "System.Numerics.dll",
                "System.Numerics.Vectors.dll",
                "System.ObjectModel.dll",
                "System.Private.CoreLib.dll",
                "System.Private.DataContractSerialization.dll",
                "System.Private.Uri.dll",
                "System.Private.Xml.dll",
                "System.Private.Xml.Linq.dll",
                "System.Reflection.DispatchProxy.dll",
                "System.Reflection.dll",
                "System.Reflection.Emit.dll",
                "System.Reflection.Emit.ILGeneration.dll",
                "System.Reflection.Emit.Lightweight.dll",
                "System.Reflection.Extensions.dll",
                "System.Reflection.Metadata.dll",
                "System.Reflection.Primitives.dll",
                "System.Reflection.TypeExtensions.dll",
                "System.Resources.Reader.dll",
                "System.Resources.ResourceManager.dll",
                "System.Resources.Writer.dll",
                "System.Runtime.CompilerServices.Unsafe.dll",
                "System.Runtime.CompilerServices.VisualC.dll",
                "System.Runtime.dll",
                "System.Runtime.Extensions.dll",
                "System.Runtime.Handles.dll",
                "System.Runtime.InteropServices.dll",
                "System.Runtime.InteropServices.JavaScript.dll",
                "System.Runtime.InteropServices.RuntimeInformation.dll",
                "System.Runtime.Intrinsics.dll",
                "System.Runtime.Loader.dll",
                "System.Runtime.Numerics.dll",
                "System.Runtime.Serialization.dll",
                "System.Runtime.Serialization.Formatters.dll",
                "System.Runtime.Serialization.Json.dll",
                "System.Runtime.Serialization.Primitives.dll",
                "System.Runtime.Serialization.Xml.dll",
                "System.Security.AccessControl.dll",
                "System.Security.Claims.dll",
                "System.Security.Cryptography.Algorithms.dll",
                "System.Security.Cryptography.Cng.dll",
                "System.Security.Cryptography.Csp.dll",
                "System.Security.Cryptography.dll",
                "System.Security.Cryptography.Encoding.dll",
                "System.Security.Cryptography.OpenSsl.dll",
                "System.Security.Cryptography.Primitives.dll",
                "System.Security.Cryptography.X509Certificates.dll",
                "System.Security.dll",
                "System.Security.Principal.dll",
                "System.Security.Principal.Windows.dll",
                "System.Security.SecureString.dll",
                "System.ServiceModel.Web.dll",
                "System.ServiceProcess.dll",
                "System.Text.Encoding.CodePages.dll",
                "System.Text.Encoding.dll",
                "System.Text.Encoding.Extensions.dll",
                "System.Text.Encodings.Web.dll",
                "System.Text.Json.dll",
                "System.Text.RegularExpressions.dll",
                "System.Threading.Channels.dll",
                "System.Threading.dll",
                "System.Threading.Overlapped.dll",
                "System.Threading.Tasks.Dataflow.dll",
                "System.Threading.Tasks.dll",
                "System.Threading.Tasks.Extensions.dll",
                "System.Threading.Tasks.Parallel.dll",
                "System.Threading.Thread.dll",
                "System.Threading.ThreadPool.dll",
                "System.Threading.Timer.dll",
                "System.Transactions.dll",
                "System.Transactions.Local.dll",
                "System.ValueTuple.dll",
                "System.Web.dll",
                "System.Web.HttpUtility.dll",
                "System.Windows.dll",
                "System.Xml.dll",
                "System.Xml.Linq.dll",
                "System.Xml.ReaderWriter.dll",
                "System.Xml.Serialization.dll",
                "System.Xml.XDocument.dll",
                "System.Xml.XmlDocument.dll",
                "System.Xml.XmlSerializer.dll",
                "System.Xml.XPath.dll",
                "System.Xml.XPath.XDocument.dll",
                "WindowsBase.dll",
                "WinRT.Runtime.dll"
        };

        for (String fileName : csharpFiles) {
            String fullResourcePath = "/" + resourcePrefix + fileName;

            try (InputStream is = NowPlayingFileManager.class.getResourceAsStream(fullResourcePath)) {
                if (is == null) {
                    System.err.println("[NowPlayingMod ERROR] Could not find resource in mod (using Class.getResourceAsStream): " + fullResourcePath);
                    continue;
                }

                Path targetFile = targetDirectory.resolve(fileName);

                Files.createDirectories(targetFile.getParent());

                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[NowPlayingMod] Copied dev resource: " + fileName);

            } catch (IOException e) {
                System.err.println("[NowPlayingMod ERROR] Failed to copy dev resource " + fileName + ": " + e.getMessage());
                throw e;
            }
        }
    }
}