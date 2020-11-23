# Changelog
All notable changes to Adblock Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.1.2] - 2020-11-23 - [!414](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/414)
### Fixed
- Added null check to waitForSitekeyCheck(). Improved other null checks to avoid race condition between the null check and use by capturing local references. [!411](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/411)

## [4.1.1] - 2020-10-26 - [!379](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/379)
### Fixed
- Referrers mapping is not cleared [!375](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/375)
- Website elements are still blocked though website is allowlisted [!368](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/368)
- Improved whitelisting with broken referrers [!377](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/377)

## [4.1.0] - 2020-10-08 - [!362](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/362)
### Fixed
- SiteKey verification happens after main frame being unblocked [!355](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/355)
- Ensures the Domains allow list is not null while in engine init [!359](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/359)
- Upgrade case for disabled-by-default state of libadblockplus-android [!351](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/351)
- Invalid thread usage in AdblockBridge.initAdblockHelper
- TabState is not saved in WebView demo app [!334](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/334)
- Handle duplicated HTTP response headers [!332](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/332)

### Changed
- Bump compile and target version from 28 to 29 [!346](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/346)
- Remove special `isDomainWhitelisted` handling [!348](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/348)
- Remove updater from libadblockplus-android [!347](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/347)
- Improve whitelisting UX [!335](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/335)
- Move disk reading code to a background thread to address possible ANRs [!336](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/336)

### Added
- Introduce semantic versioning [!358](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/358)
- Introduce the changelog [!350](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/350)

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
