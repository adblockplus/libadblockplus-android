# Changelog
All notable changes to Adblock Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), 
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Fixed
- Decision on blocking the resources is now held until the sitekey is checked - [!355](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/355) 

### Added
- Semantic Versioning is introduced. [!358](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/358)

## [4.0] - 2020-08-26 - [!333](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/333)
### Changed
- Sitekey retrieval mechanism refactored. [!196](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/196)
- libadblockplus dependency updated. [!307](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/307) [!314](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/314)

### Fixed
- Bing not loading correctly with Adblock WebView 3.23. [!296](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/296)
- Don't read error InputStream for redirections. [!323](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/323)
- Sitekey rules from non AA subscription are not effectively used when
  AA is disabled. [!308](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/308)
- Fix potential crash in AdblockWebView. [!305](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/305)
- Fix missed detected url on url file type detector. [!316](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/316)
