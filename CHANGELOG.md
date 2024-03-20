# Changelog
### 0.1.7 (Mar 19, 2024)

Updated Requirements

- Updated to use gradle `8.4` and android.build.tools `8.3.0`.
- Removed/replaced deprecated methods.

### 0.1.6 (July 12, 2023)

Bug Fixes

- Updated build key so automatic versioning can occur.

### 0.1.5 (July 12, 2023)

Updated Requirements

- Updated to use gradle `8.0.2` and android.build.tools `8.0.2`.

### 0.1.4 (December 9, 2021)

Bug Fixes

- Fixed failure issue when dependencies include internal dependencies as well as external ones.
- Removed references to jcenter
- Updated to use gradle 7.0.2 & android.build.tools 7.0.3
- Updated the compileSDKVersion to 31

### 0.1.3 (September 13, 2021)

Updated Requirements

- Using `apkscale` now requires Android Gradle Plugin 7.0.0+,  Gradle 7.0.0+, and Java 11

Enhancements

- Added `humanReadable` configuration property that enables a user to toggle the use of `apkanalyzer` `--human-readable` flag. This property is `true` by default.

```groovy
apkscale {
    humanReadable = false
}
```

Bug Fixes

- Apkscale now includes a library's dependencies in the size report. Fixes [#5](https://github.com/twilio/apkscale/issues/5).

### 0.1.2 (March 5, 2021)

Enhancements

- Now published to MavenCentral

### 0.1.1 (August 28, 2020)

Bug Fixes

- Fixed a bug where the measure task could not be executed with projects that set `android.ndkVersion`

### 0.1.0 (July 28, 2020)

This release marks the first iteration of apkscale: a Gradle plugin to measure the app size impact of Android libraries.

## Requirements

* Android SDK
* Apkscale can only be applied within a `com.android.library` project.
* [apkanalyzer](https://developer.android.com/studio/command-line/apkanalyzer) must be in your machine's path
* Android Gradle Plugin 4.0.0+

## Usage

Add the following to your project's buildscript section.

```groovy
buildscript {
    repositories {
        mavenCentral()
        maven { url 'https://repo.gradle.org/gradle/libs-releases' }
    }
    classpath "com.twilio:apkscale:0.1.0"
}
```

Apply the plugin in your Android library project.

```groovy
apply plugin: 'com.android.library'
apply plugin: 'com.twilio.apkscale'

apkscale {
    // Optional parameter to provide size reports for each ABI in addition to the default universal ABI
    abis = ['x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a']
}
```

Apkscale adds a `measureSize` task to your Android library module and, when run, scans the output directory of your library and measures the size of each .aar file present. Apkscale outputs the size report to a json file located at `<yourProjectBuildDir>/apkscale/build/outputs/reports/apkscale.json`. The json file contains an array of elements that provide a size report for each .aar file measured. Apkscale writes the size in a `--human-readable` format as specified by [apkanalyzer](https://developer.android.com/studio/command-line/apkanalyzer). Reference the example below.

```json5
[
  {
    "library": "your-library-release.aar",
    "size": {
      // Included in all reports
      "universal": "21.9MB",

      // Included as specified by the abis parameter
      "x86": "6MB",
      "x86_64": "6.1MB",
      "armeabi-v7a": "4.8MB",
      "arm64-v8a": "5.7MB"
    }
  }
]
```

The following demonstrates how to read the Apkscale output and convert it to a markdown table.

```groovy
task generateSizeReport {
    dependsOn('measureSize')

    doLast {
        def sizeReport = "Size Report\n" +
                "\n" +
                "| ABI             | APK Size Impact |\n" +
                "| --------------- | --------------- |\n"
        def apkscaleOutputFile = file("$buildDir/apkscale/build/outputs/reports/apkscale.json")
        def jsonSlurper = new JsonSlurper()
        def apkscaleOutput = jsonSlurper.parseText(apkscaleOutputFile.text).get(0)

        apkscaleOutput.size.each { arch, sizeImpact ->
            videoAndroidSizeReport += "| ${arch.padRight(16)}| ${sizeImpact.padRight(16)}|\n"

        }
        println(sizeReport)
    }
}
```
