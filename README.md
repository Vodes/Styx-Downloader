## Styx-Downloader
An auto-downloader for ongoing shows.<br>
Technically only a CLI Tool because configuration is done via the website.
###### (Note that this always copies/moves files and does not hardlink!)
### Features
- Automatic downloads of<br>
	- XDCC Transfers
	- Files on an FTP server
	- Torrents via RSS feeds (with temporary and permanent seeding)[^1] [^2]
	- *(Planned)* Usenet/nzb via RSS or rather Torznab feeds
- File Parsing/Detection via regex
- Automatic renaming and applying of mkv title tags based on templates
- Various types of auto muxing/processing via [muxtools-styx](https://github.com/Vodes/muxtools-styx)
	- Restyling subtitles
	- *(Planned)* Applying tpp to subtitles
	- Keeping audio/video/subtitle tracks from previous version when upgrading[^3]
	- Auto-fixing tagging issues
- Running arbitrary commands on a file after everything else


[^1]: ##### The downloader only handles XDCC and FTP downloads internally. Other stuff needs external (e. g. torrent) clients to interact with.
[^2]: ##### This currently only supports the [Flood]() API but I am open to PRs until I add more support myself.
[^3]: ##### When you for example grab WEB-DL's first then upgrade to something like Commie but want to keep the WEB-DL video
