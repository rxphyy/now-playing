![ ](https://i.imgur.com/WI65qda.png)

This mod lets you view what song is currently playing on your PC without the need for you to Alt-Tab all the time to check. It displays a clear overlay in-game to allow you to keep the shuffle going on and not have to interrupt your mining to check who, what, where, when is playing in your headphones!

<br/>

### 🖥️ Completely local

No weird sign-ins to any music service, no authorization needed; This mod runs completely local on **your** computer.

<details>
  <summary>📡 How is that possible?</summary>
  
  The mod utilizes a C# console application that runs in the background to fetch what is currently playing on your PC via <a href="https://learn.microsoft.com/en-us/uwp/api/?view=winrt-26100">WinRT</a>. It hosts this data locally in a Web server, which the mod can then fetch easily via HTTP.
  
  <br>
  
  When the game is running, you can even access these URLs yourself to see it in action:

  ---
  <a href="http://localhost:58888/media-info" target="blank">
    <pre><strong>/media_info</strong> 🎵</pre>
  </a>
  This URL will show you what is currently playing on your computer and includes info such as the title, the artist's name, the app that's currently playing the media (ex.: "Spotify.exe"), the playing status (ex.: "Playing", "Paused"), the current timeline position, the start time and the end time (duration/length) of the media.
  
  ---
  <a href="http://localhost:58888/media-image.jpg" target="blank">
    <pre><strong>/media_image.jpg</strong> 🖼️</pre>
  </a>
  This URL will show you what is currently playing on your computer and includes info such as the title, the artist's name, the app that's currently playing the media (ex.: "Spotify.exe"), the playing status (ex.: "Playing", "Paused"), the current timeline position, the start time and the end time (duration/length) of the media.
<br/>
<br/>
<table width="400">
  <tr>
    <td style="text-align: center;">
      <img src="https://i.imgur.com/lg2PE1f.png" width="100" /><br>
    </td>
    <td style="text-align: center;">
      <img src="https://i.imgur.com/aQt5eBU.png" width="200" /><br>
    </td>
    <td style="text-align: center;">
      <img src="https://i.imgur.com/X7825Fz.png" width="130" /><br>
    </td>
  </tr>
  <tr>
    <td style="text-align: center;">
      <em>When watching a movie in an app like Netflix, or directly from the Web browser, the service often displays the current media's poster art.</em>
    </td>
    <td style="text-align: center;">
      <em>The same happens when watching a YouTube video in your browser of choice.</em>
    </td>
    <td style="text-align: center;">
      <em>Some apps like Spotify embed their branding directly in the image they provide, which is why we can see the Spotify logo under the cover art for this song.</em>
    </td>
  </tr>
</table>

  ---
  
  <a href="http://localhost:58888/play-pause" target="blank">
    <pre>GET<strong> /play_pause ⏯️</strong></pre>
  </a>
  🚨 Don't be alarmed, but when opening this one, if anything was playing on your computer, it will paused, and if you open it again, well it should play again. This is completely normal and is how the mod can provide play/pause controls.
  
  ---
  <a href="http://localhost:58888/skip-next" target="blank">
    <pre>GET<strong> /skip-next ⏭️</strong></pre>
  </a>
  🚨 This URL should skip the currently playing media and go the next one, so don't be alarmed if it skips your favorite song when opening it up.
  <a href="http://localhost:58888/skip-previous" target="blank">
    <pre>GET<strong> /skip-previous ⏮️</strong></pre>
  </a>
  🚨 Uno reverses the URL above it.
  
  ---

</details>

<br/>

### ⚙️ Easily customizable

With its implementation into <a href="https://modrinth.com/mod/modmenu" target="_blank">Mod Menu</a>, this mod allows you to customize a bunch of settings to make this the best experience for your playthrough.

<details>
  <summary>🔨 Customization options</summary>

| Setting             | Description | Options |
| ------------------- | ------- | ------- |
| Horizontal position | Controls the mod widget's position on the X axis (horizontally).    | Slider (from 0 to your screen's width). Default is maximum.    |
| Vertical position   | Controls the mod widget's position on the Y axis (vertically).     | Slider (from 0 to your screen's height). Default is 10.    |
| Font size           | Controls the size of the text on screen.    | Slider (from 5 to 25). Default is 10. |
| Background opacity  | How transparent should the background of the widget be?  | Slider (from 0 to 100). Default is 90   |
| Show cover art      | Should the cover art for what you're listening to be displayed? See the section above for more info.    | Checkbox, default is true.    |
| Show timeline       | Display the track length aswell as the current position?    | Checkbox, default is true.    |
| Show artist name    | Show the artist name for the media that's currently playing?    | Checkbox, default is true.    |
| Show media title    | Show the title for the media that's currently playing?    | Checkbox, default is true.    |
| Show play status icon       | Display an icon next to the timeline that shows if the media is playing or paused?     | Checkbox, default is true.   |


</details>
