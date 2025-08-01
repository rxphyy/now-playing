using System.Net;
using System.Text;
using Windows.Media.Control;

class Program {
    private static GlobalSystemMediaTransportControlsSessionManager? _sessionManager;
    private static GlobalSystemMediaTransportControlsSession? _currentSession;

    private static string _title = "";
    private static string _artist = "";
    private static string _app = "";
    private static string _playbackStatus = "";
    private static string _playbackPosition = "";
    private static string _playbackStart = "";
    private static string _playbackEnd = "";
    private static byte[]? _imageBytes = null;

    private static TimeSpan _lastKnownPosition = TimeSpan.Zero;
    private static DateTime _lastKnownPositionTimestamp = DateTime.UtcNow;
    private static double _playbackRate = 1.0;
    private static bool _isPlaying = false;
    private static TimeSpan _endTime = TimeSpan.MaxValue;

    private static string _lastLoggedTitle = "";
    private static string _lastLoggedArtist = "";
    private static string _lastLoggedPlaybackStatus = "";

    static async Task Main() {
        _sessionManager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();

        Console.WriteLine($"{DateTime.Now} Listening for media changes...");

        await DiscoverAndSubscribeToSession();

        _sessionManager.CurrentSessionChanged += async (s, e) =>
        {
            Console.WriteLine($"{DateTime.Now} Global Session Changed event detected.");
            await DiscoverAndSubscribeToSession();
        };

        _sessionManager.SessionsChanged += async (s, e) =>
        {
            Console.WriteLine($"{DateTime.Now} Sessions collection changed event detected (app opened/closed).");
            await DiscoverAndSubscribeToSession();
        };

        _ = StartHttpServer();
        _ = PollPlaybackPosition();

        await Task.Delay(-1);
    }

    private static async Task DiscoverAndSubscribeToSession() {
        GlobalSystemMediaTransportControlsSession? sessionToSubscribe = null;

        var allSessions = _sessionManager?.GetSessions()?.ToList();

        if (allSessions != null && allSessions.Any())
        {
            var playingSessions = allSessions
                .Where(s => s.GetPlaybackInfo()?.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing)
                .ToList();

            if (playingSessions.Count == 1) {
                sessionToSubscribe = playingSessions.First();
                Console.WriteLine($"{DateTime.Now} Found exactly one playing session. Prioritizing: {sessionToSubscribe.SourceAppUserModelId}");
            } else if (playingSessions.Count > 1) {
                sessionToSubscribe = _sessionManager?.GetCurrentSession();
                if (sessionToSubscribe != null) {
                    Console.WriteLine($"{DateTime.Now} Multiple sessions playing. Using Windows' default active session: {sessionToSubscribe.SourceAppUserModelId}");
                } else {
                    Console.WriteLine($"{DateTime.Now} Multiple sessions playing, but no default active session found. Falling back to first available.");
                    sessionToSubscribe = allSessions.FirstOrDefault();
                }
            } else {
                sessionToSubscribe = _sessionManager?.GetCurrentSession();
                if (sessionToSubscribe != null) {
                    Console.WriteLine($"{DateTime.Now} No sessions currently playing. Falling back to Windows' default active session: {sessionToSubscribe.SourceAppUserModelId}");
                } else {
                    Console.WriteLine($"{DateTime.Now} No sessions found. Clearing info.");
                }
            }
        } else {
            Console.WriteLine($"{DateTime.Now} No active media sessions found by manager.");
        }

        if (sessionToSubscribe != _currentSession) {
            Console.WriteLine($"{DateTime.Now} Session to subscribe has changed.");
            SubscribeToSession(sessionToSubscribe);
        } else {
            Console.WriteLine($"{DateTime.Now} Session to subscribe is the same as current. No re-subscription needed.");
            if (_currentSession != null) {
                _ = UpdateMediaInfoAsync(_currentSession);
            } else {
                ClearMediaInfo();
                CheckAndLogMediaInfo(true);
            }
        }
    }

    private static void SubscribeToSession(GlobalSystemMediaTransportControlsSession? session) {
        if (_currentSession != null) {
            _currentSession.MediaPropertiesChanged -= OnMediaPropertiesChanged;
            _currentSession.PlaybackInfoChanged -= OnPlaybackInfoChanged;
            Console.WriteLine($"{DateTime.Now} Unsubscribed from previous session events (App: {_currentSession.SourceAppUserModelId}).");
        }

        _currentSession = session;

        if (_currentSession == null) {
            Console.WriteLine($"{DateTime.Now} No active media session to subscribe to. Clearing info.");
            ClearMediaInfo();
            CheckAndLogMediaInfo(true); // Force log the cleared state
            return;
        }

        _currentSession.MediaPropertiesChanged += OnMediaPropertiesChanged;
        _currentSession.PlaybackInfoChanged += OnPlaybackInfoChanged;
        Console.WriteLine($"{DateTime.Now} Subscribed to new session events (App: {_currentSession.SourceAppUserModelId}).");

        _ = UpdateMediaInfoAsync(_currentSession);
    }

    private static void ClearMediaInfo() {
        _title = "(none)";
        _artist = "";
        _app = "";
        _imageBytes = null;
        _playbackStatus = "Stopped";
        _playbackPosition = "";
        _playbackStart = "";
        _playbackEnd = "";
        _lastKnownPosition = TimeSpan.Zero;
        _lastKnownPositionTimestamp = DateTime.UtcNow;
        _playbackRate = 1.0;
        _isPlaying = false;
        _endTime = TimeSpan.MaxValue;
    }

    private static async Task PollPlaybackPosition() {
        while (true) {
            try {
                if (_isPlaying && _currentSession != null && _endTime != TimeSpan.MaxValue) {
                    var elapsed = DateTime.UtcNow - _lastKnownPositionTimestamp;
                    TimeSpan simulatedPosition = _lastKnownPosition + TimeSpan.FromSeconds(elapsed.TotalSeconds * _playbackRate);

                    if (simulatedPosition > _endTime) {
                        simulatedPosition = _endTime;
                    }
                    _playbackPosition = simulatedPosition.ToString(@"hh\:mm\:ss\.fff");
                } else if (_currentSession == null) {
                    _playbackPosition = "";
                    _playbackStart = "";
                    _playbackEnd = "";
                }
            } catch (Exception ex) {
                Console.WriteLine($"{DateTime.Now} Error polling playback position: {ex.Message}");
                _playbackPosition = "";
                _playbackStart = "";
                _playbackEnd = "";
            }
            
            await Task.Delay(250);
        }
    }

    private static async Task StartHttpServer() {
        var listener = new HttpListener();
        listener.Prefixes.Add("http://localhost:58888/");
        listener.Start();
        Console.WriteLine($"{DateTime.Now} HTTP server started at http://localhost:58888");

        while (true) {
            var context = await listener.GetContextAsync();
            var response = context.Response;
            var path = context.Request.Url!.AbsolutePath;

            Console.WriteLine($"{DateTime.Now} HTTP Request: {path}");

            try {
                if (path == "/media-info") {
                    string json =
                        $"{{" +
                        $"\"title\":\"{Escape(_title)}\"," +
                        $"\"artist\":\"{Escape(_artist)}\"," +
                        $"\"app\":\"{Escape(_app)}\"," +
                        $"\"status\":\"{Escape(_playbackStatus)}\"," +
                        $"\"position\":\"{Escape(_playbackPosition)}\"," +
                        $"\"start\":\"{Escape(_playbackStart)}\"," +
                        $"\"end\":\"{Escape(_playbackEnd)}\"" +
                        $"}}";
                    byte[] buffer = Encoding.UTF8.GetBytes(json);
                    response.ContentType = "application/json";
                    response.OutputStream.Write(buffer, 0, buffer.Length);
                } else if (path == "/media-image.jpg") {
                    if (_imageBytes != null) {
                        response.ContentType = "image/jpeg";
                        response.OutputStream.Write(_imageBytes, 0, _imageBytes.Length);
                    } else {
                        response.StatusCode = 404;
                        Console.WriteLine($"{DateTime.Now} Image requested but no image available. Returning 404.");
                    }
                } else if (path == "/play-pause") {
                    _currentSession?.TryTogglePlayPauseAsync();
                    Console.WriteLine($"{DateTime.Now} Attempting Play/Pause toggle on current session.");
                } else if (path == "/skip-next") {
                    _currentSession?.TrySkipNextAsync();
                    Console.WriteLine($"{DateTime.Now} Attempting Skip Next on current session.");
                } else if (path == "/skip-previous") {
                    _currentSession?.TrySkipPreviousAsync();
                    Console.WriteLine($"{DateTime.Now} Attempting Skip Previous on current session.");
                } else {
                    response.StatusCode = 404;
                    Console.WriteLine($"{DateTime.Now} Unknown path requested: {path}. Returning 404.");
                }
            } catch (Exception ex) {
                Console.WriteLine($"{DateTime.Now} Error handling HTTP request for {path}: {ex.Message}");
                response.StatusCode = 500;
            } finally {
                response.OutputStream.Close();
            }
        }
    }

    private static async void OnMediaPropertiesChanged(GlobalSystemMediaTransportControlsSession sender, object args) {
        if (sender == _currentSession) {
            Console.WriteLine($"{DateTime.Now} MediaPropertiesChanged event triggered for tracked session.");
            await UpdateMediaInfoAsync(sender);
        } else {
            Console.WriteLine($"{DateTime.Now} MediaPropertiesChanged event triggered for a non-tracked session. Re-evaluating active session.");
            await DiscoverAndSubscribeToSession();
        }
    }

    private static async void OnPlaybackInfoChanged(GlobalSystemMediaTransportControlsSession sender, object args) {
        if (sender == _currentSession) {
            Console.WriteLine($"{DateTime.Now} PlaybackInfoChanged event triggered for tracked session.");
            await UpdateMediaInfoAsync(sender);
        } else {
            Console.WriteLine($"{DateTime.Now} PlaybackInfoChanged event triggered for a non-tracked session. Re-evaluating active session.");
            await DiscoverAndSubscribeToSession();
        }
    }

    private static async Task UpdateMediaInfoAsync(GlobalSystemMediaTransportControlsSession session) {
        try {
            var mediaProperties = await session.TryGetMediaPropertiesAsync();
            var playbackInfo = session.GetPlaybackInfo();
            var timeline = session.GetTimelineProperties();

            string newTitle = mediaProperties.Title ?? "";
            string newArtist = mediaProperties.Artist ?? "";
            string newApp = session.SourceAppUserModelId ?? "";
            string newPlaybackStatus = playbackInfo.PlaybackStatus.ToString();

            _title = newTitle;
            _artist = newArtist;
            _app = newApp;
            _playbackStatus = newPlaybackStatus;

            _lastKnownPosition = timeline.Position;
            _lastKnownPositionTimestamp = DateTime.UtcNow;
            _playbackRate = playbackInfo.PlaybackRate ?? 1.0;
            _isPlaying = playbackInfo.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;

            _playbackStart = timeline.StartTime.ToString(@"hh\:mm\:ss\.fff");
            _playbackEnd = timeline.EndTime.ToString(@"hh\:mm\:ss\.fff");
            _endTime = timeline.EndTime; 

            _playbackPosition = _lastKnownPosition.ToString(@"hh\:mm\:ss\.fff");


            var thumbnail = mediaProperties.Thumbnail;
            if (thumbnail != null) {
                using var stream = await thumbnail.OpenReadAsync();
                using var memoryStream = new MemoryStream();
                await stream.AsStreamForRead().CopyToAsync(memoryStream);
                _imageBytes = memoryStream.ToArray();
                Console.WriteLine($"{DateTime.Now} New thumbnail image loaded for session: {newApp}.");
            } else {
                _imageBytes = null;
                Console.WriteLine($"{DateTime.Now} No thumbnail image available for session: {newApp}.");
            }

            CheckAndLogMediaInfo(false); // Do not force initial log
        } catch (Exception ex) {
            Console.WriteLine($"{DateTime.Now} Failed to read media info during update for session {session?.SourceAppUserModelId}: {ex.Message}");
             if (session == _currentSession) {
                ClearMediaInfo();
                CheckAndLogMediaInfo(true);
                _ = DiscoverAndSubscribeToSession();
            }
        }
    }

    private static void CheckAndLogMediaInfo(bool forceLog) {
        bool changed = false;

        if (!string.Equals(_title, _lastLoggedTitle, StringComparison.Ordinal) ||
            !string.Equals(_artist, _lastLoggedArtist, StringComparison.Ordinal) ||
            !string.Equals(_playbackStatus, _lastLoggedPlaybackStatus, StringComparison.Ordinal))
        {
            changed = true;
        }

        if (changed || forceLog) {
            string logMessage;
            if (_playbackStatus.Equals("Playing", StringComparison.OrdinalIgnoreCase)) {
                logMessage = $"STARTED playing: {_title} - {_artist} ({_app})";
            } else if (_playbackStatus.Equals("Paused", StringComparison.OrdinalIgnoreCase)) {
                logMessage = $"PAUSED: {_title} - {_artist} ({_app})";
            } else if (_playbackStatus.Equals("Stopped", StringComparison.OrdinalIgnoreCase) || _playbackStatus.Equals("Closed", StringComparison.OrdinalIgnoreCase)) {
                logMessage = $"STOPPED playing: {_title} - {_artist} ({_app})";
            } else {
                logMessage = $"Playback Status: {_playbackStatus} - {_title} - {_artist} ({_app})";
            }

            Console.WriteLine($"{DateTime.Now} {logMessage}");

            _lastLoggedTitle = _title;
            _lastLoggedArtist = _artist;
            _lastLoggedPlaybackStatus = _playbackStatus;
        }
    }

    private static string Escape(string input) {
        if (input == null) 
            return "";

        return input.Replace("\\", "\\\\").Replace("\"", "\\\"");
    }
}