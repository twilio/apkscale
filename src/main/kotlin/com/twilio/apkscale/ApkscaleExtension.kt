package com.twilio.apkscale

/**
 * Configuration options for the Apkscale Gradle plugin.
 */
open class ApkscaleExtension(
    /**
     * If set to `true`, Apkscale will generate size reports for each of the specified ABIs in addition
     * to the universal ABI. By default this set is empty and Apkscale provides a size report for the
     * universal ABI.
     */
    var abis: Set<String> = emptySet(),

    /**
     * If set to `true`, Apkscale will generate size reports in a human readable format. Defaults to `true`.
     */
    var humanReadable: Boolean = true,

    /**
     * if set to 'true', Apkscale will enable compression of shared object files to measure the size of the library. Defaults to 'true'.
     */
    var useLegacyPackaging: Boolean = true,
)
