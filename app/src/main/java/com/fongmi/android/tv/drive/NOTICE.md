Anonymous share-check endpoints and selected provider response fields were researched from
WebHTV `DriveCheckService.java` at commit `4e30ffaf219b1db15fe5662aeff6f9820e2b32bf`:
https://github.com/webhtv/webhtv/blob/4e30ffaf219b1db15fe5662aeff6f9820e2b32bf/app/src/main/java/com/fongmi/android/tv/service/DriveCheckService.java

WebHTV is licensed under GNU GPL version 3; its license is available at
https://github.com/webhtv/webhtv/blob/4e30ffaf219b1db15fe5662aeff6f9820e2b32bf/LICENSE.md
This repository also distributes the GPL license in its root LICENSE.md.

The parser, transport, cancellation, bounded cache and strict classifiers here were implemented
for TV. No upstream batch executor, persistent share history, cookie collection, logging of
share URLs or arbitrary remote URLs is included. The Aliyun share-token metadata endpoint also
checks a supplied extraction code; no file content or download URL is requested.
