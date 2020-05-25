package com.tailoredapps.gradle.localize

import kotlinx.coroutines.runBlocking
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

private const val PLUGIN_CONFIGURATION_EXTENSION_NAME = "localizeConfig"
private const val DEFAULT_LOCALIZATION_PATH = "./src/main/res"
private const val DEFAULT_BASE_LANGUAGE = "en"

private const val PLUGIN_TASK_GROUP_NAME = "localization"

//created using this tutorial: https://dzone.com/articles/the-complete-custom-gradle-plugin-building-tutoria
class GradleLocalizePlugin : Plugin<Project> {

    private val localize: Localize by lazy { Localize() }

    override fun apply(project: Project) {
        val extension = project.extensions.create(PLUGIN_CONFIGURATION_EXTENSION_NAME, LocalizeExtension::class.java)
        project.tasks.create("localize") { task ->
            task.doLast {
                runLocalize(project.projectDir, extension)
            }
        }.apply {
            description = "Imports strings from a localization sheet"
            group = PLUGIN_TASK_GROUP_NAME
        }
        val checkLocalization = project.tasks.create("checkLocalization") { task ->
            task.doLast {
                runCheckLocalization(project.projectDir, extension)
            }
        }.apply {
            description = "Checks whether the local localizations are up-to-date."
            group = PLUGIN_TASK_GROUP_NAME
        }

        if (extension.addToCheckTask) {
            project.tasks.getByName("check").dependsOn(checkLocalization)
        }

    }

    private fun checkConfiguration(projectPath: File, extension: LocalizeExtension) {
        if (extension.serviceAccountCredentialsFile.isBlank()) {
            throw LocalizeConfigurationException(
                "'serviceAccountCredentialsFile' not set. This needs to be set to the path of the credentials file where the service account credentials are stored.\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    serviceAccountCredentialsFile = \"./google_drive_credentials.json\"\n" +
                        "}"
            )
        }

        if (extension.sheetId.isBlank()) {
            throw LocalizeConfigurationException(
                "'sheetId' not set. This needs to be set to the id of the google spreadsheet in which the localization strings are entered.\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    sheetId = \"1fwRj1ZFPu2XlrDqkaqmIpJulqR5OVFEZnN35a9v37yc\"\n" +
                        "}"
            )
        }

        if (extension.languageTitles.isEmpty()) {
            throw LocalizeConfigurationException(
                "'languageTitles' not set (or set to an empty array). This needs to be set to the column header of the languages you want to import.\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    languageTitles = [\"en\", \"de\", \"ru\"]\n" +
                        "}"
            )
        }

        if (extension.baseLanguage.isBlank()) {
            throw LocalizeConfigurationException(
                "'baseLanguage' is set to an invalid value. Default is \"$DEFAULT_BASE_LANGUAGE\". Needs to be set to a value present in 'languageTitles'. This represents the language which is placed as a default language in the localizations (so as 'values/strings.xml' resource in Android, which is the fallback language if a language is not supported).\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    baseLanguage = \"$DEFAULT_BASE_LANGUAGE\"\n" +
                        "}"
            )
        }

        if (extension.languageTitles.none { it == extension.baseLanguage }) {
            throw LocalizeConfigurationException(
                "'baseLanguage' is set to a value not present in 'languageTitles'. Needs to be set to a value present in 'languageTitles' (which is currently set to ${extension.languageTitles.joinToString()}, so any of those values is valid). This represents the language which is placed as a default language in the localizations (so as 'values/strings.xml' resource in Android, which is the fallback language if a language is not supported).\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    baseLanguage = \"${extension.languageTitles.first()}\"\n" +
                        "}"
            )
        }

        if (extension.localizationPath.isBlank()) {
            throw LocalizeConfigurationException(
                "'localizationPath' is set to an invalid value. Default is \"$DEFAULT_LOCALIZATION_PATH\". This is needed as a local path where to save the string resource xml files to. Normally this only needs to be changed if you need to place the string xml files in another module than the default app module.\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    localizationPath = \"$DEFAULT_LOCALIZATION_PATH\"\n" +
                        "}"
            )
        }

        val serviceAccountCredentialsFile = File(projectPath, extension.serviceAccountCredentialsFile)
        if (serviceAccountCredentialsFile.exists().not()) {
            throw LocalizeConfigurationException(
                "${serviceAccountCredentialsFile.absolutePath} not found (defined as 'serviceAccountCredentialsFile': \"${extension.serviceAccountCredentialsFile}\"). This needs to be set to the path of the credentials file where the service account credentials are stored.\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    serviceAccountCredentialsFile = \"./google_drive_credentials.json\"\n" +
                        "}"
            )
        }
        if (serviceAccountCredentialsFile.canRead().not()) {
            throw LocalizeConfigurationException(
                "${serviceAccountCredentialsFile.absolutePath} cannot be read (defined as 'serviceAccountCredentialsFile': \"${extension.serviceAccountCredentialsFile}\"). This needs to be set to the path of the credentials file where the service account credentials are stored.\n" +
                        "Example:\n" +
                        "$PLUGIN_CONFIGURATION_EXTENSION_NAME {\n" +
                        "    serviceAccountCredentialsFile = \"./google_drive_credentials.json\"\n" +
                        "}"
            )
        }
    }

    private fun runLocalize(projectPath: File, extension: LocalizeExtension) {
        checkConfiguration(projectPath, extension)

        runBlocking {
            localize.localize(
                sheetId = extension.sheetId,
                serviceAccountCredentialsFile = File(projectPath, extension.serviceAccountCredentialsFile),
                languageTitles = extension.languageTitles,
                baseLanguage = extension.baseLanguage,
                localizationPath = File(projectPath, extension.localizationPath),
                addComments = extension.addComments
            )
        }
    }

    private fun runCheckLocalization(projectPath: File, extension: LocalizeExtension) {
        checkConfiguration(projectPath, extension)

        runBlocking {
            localize.check(
                sheetId = extension.sheetId,
                serviceAccountCredentialsFile = File(projectPath, extension.serviceAccountCredentialsFile),
                languageTitles = extension.languageTitles,
                baseLanguage = extension.baseLanguage,
                localizationPath = File(projectPath, extension.localizationPath),
                addComments = extension.addComments
            )
        }
    }
}

class LocalizeConfigurationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException("$message\nCheck out the README on how to configure the localize plugin.", cause)


/**
 * @param serviceAccountCredentialsFile The local path to the credentials file for the service-account.
 * @param sheetId The id of the spreadsheet which contains the localization entries. You can get this id from the link
 * to your spreadsheet.
 * @param languageTitles The list of column titles of the languages in the localization sheet (which is simultaneously
 * also the list of local language folders which are created, so those should be e.g. `de` for german or `en` for
 * english, and the column titles in the sheet should be named the same.
 * @param baseLanguage The language (one of the values from [languageTitles]) which should be the default language, which is placed
 * in the `values` folder (so if this is set to `en`, there will be no `values-en` folder created, but the english
 * localizations will be placed in the `values` folder).
 * @param localizationPath The base directory path to put the localizations in. This defaults to the default path within
 * an app module to put the string resources to. Change this if you want to have your localizations put somewhere else.
 * @param addToCheckTask Whether this plugin should add the `checkLocalization` task to the default `check` task.
 * @param addComments Whether the comments from the spreadsheet should be added to the strings.xml files (as comments)
 * as well.
 */
open class LocalizeExtension(
    var serviceAccountCredentialsFile: String = "",
    var sheetId: String = "",
    var languageTitles: MutableList<String> = mutableListOf(),
    var baseLanguage: String = DEFAULT_BASE_LANGUAGE,
    var localizationPath: String = DEFAULT_LOCALIZATION_PATH,
    var addToCheckTask: Boolean = true,
    var addComments: Boolean = true
)