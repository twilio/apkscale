# Changelog

### 0.1.0

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
        jcenter()
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
    // Optional parameter to provide size reports for each API in addition to the default universal ABI
    abis = ['x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a']
}
```

Apkscale adds a `measureSize` task to your Android library module and, when run, scans the output directory of your library and measure the size of each .aar file present. Apkscale outputs the size report to a json file located at `<yourProjectBuildDir>/apkscale/build/outputs/reports/apkscale.json`. The json file contains an array of elements that provide a size report for each .aar file measured. Reference the example below.

```json
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
