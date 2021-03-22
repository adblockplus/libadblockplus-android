# Changelog
All notable changes to Adblock Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.4.0] - 2021-03-15 - [!523](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/523)
### Fixed
 - NPE crash when opening settings [!515](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/515)
 - Fix exception thrown from AdblockWebView sample app on startup [!505](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/505)

### Changed/Added
 - Pick disabled by default option from libabp [!477](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/477)

## [4.3.0] - 2021-02-09 - [!482](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/482)
### Fixed
 - Fixed user counting [!475](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/475)
 - Navigation performance gets significaly slower with 10+ tabs opened [!471](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/471)
 - Preloaded subscriptions feature does not work (in most cases) [!442](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/442)

### Changed
 - Updated FilterEngine.Matches() API after libabp update from DP-1806 [!472](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/472)
 - Passing a document url instead of parent for genericblock rule [!447](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/447)
 - Added support for other content types in HttpHeaderSitekeyExtractor [!468](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/468)

### Added
 - Adblock settings decoupled from FilterEngine and settings module [!470](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/470)

## [4.2.0] - 2021-01-15 - [!452](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/452)
### Fixed
 - Fixed race condition [!431](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/431)
 - Blocking is not working if AA is disabled [!429](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/429)
 - Cookies that are set by redirected requests are not saved [!430](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/430)
 - Test page (sitekey) is displaying incorrect result if AA list is enabled [!429](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/429)
 - Fixed exception thrown when processing gzipped response stream in httpclient [!446](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/446)

### Changed
 - Updated translation strings [!388](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/388)
 - Migrated settings into Android Architecture Components [!389](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/389)
 - libadblockplus dependency updated [!439](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/439)
 - Removed duplicate methods in libadblockplus utils [!424](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/424)
 - Implemented Utils.getDomain(final String url) without throwing [!440](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/440)

### Added
 - Enabled element hiding in iFrames as opt-in feature [!432](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/432)
 - Replaced whitelist/blacklist with more appropriate alternative [!393](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/393)

## [4.1.2] - 2020-11-23 - [!414](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/414)
### Fixed
 - Crash in JsSiteKeyExtractor.waitForSitekeyCheck [!411](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/411)

## [4.1.1] - 2020-10-26 - [!379](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/379)
### Fixed
- Referrers mapping are not cleared [!375](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/375)
- Website elements are still blocked though website is allowlisted [!368](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/368)
- Improved whitelisting with broken referrers [!377](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/377)

## [4.1.0] - 2020-10-08 - [!362](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/362)
### Fixed
- SiteKey verification happens after main frame being unblocked [!355](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/355)
- Ensures the Domains allow list are not null while in engine init [!359](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/359)
- Upgraded case for disabled-by-default state of libadblockplus-android [!351](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/351)
- Invalid thread usage in AdblockBridge.initAdblockHelper
- TabState is not saved in WebView demo app [!334](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/334)
- Handled duplicated HTTP response headers [!332](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/332)

### Changed
- Bumped compile and target version from 28 to 29 [!346](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/346)
- Removed special `isDomainWhitelisted` handling [!348](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/348)
- Removed updater from libadblockplus-android [!347](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/347)
- Improved whitelisting UX [!335](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/335)
- Moved disk reading code to a background thread to address possible ANRs [!336](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/336)

### Added
- Introduced semantic versioning [!358](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/358)
- Introduced the changelog [!350](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/350)

## [4.0] - 2020-08-26 - [!333](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/333)
### Changed
- Sitekey retrieval mechanism refactored. [!196](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/196)
- libadblockplus dependency updated. [!307](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/307) [!314](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/314)

### Fixed
- Bing not loading correctly with Adblock WebView 3.23. [!296](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/296)
- Don't read error InputStream for redirections. [!323](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/323)
- Sitekey rules from non AA subscription are not effectively used when
  AA is disabled. [!308](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/308)
- Fixed potential crash in AdblockWebView. [!305](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/305)
- Fixed missed detected url on url file type detector. [!316](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/316)
