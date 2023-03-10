def DATETIME = new Date().format('yyyy_MM_dd_HH_mm_ss', TimeZone.getTimeZone('Europe/Madrid'))
def pluginsToReviewManually = []
def pluginsDeprecated = []
def secrets = [
        [path: 'secret/notifications/discord', engineVersion: 2, secretValues: [
            [envVar: 'DISCORD_WEBHOOK', vaultKey: 'webhook']]]
    ]
pipeline {
    agent { label 'built-in' }
    options {
        //Build options
        disableConcurrentBuilds()
        buildDiscarder(
          logRotator (
                       artifactDaysToKeepStr: '10',
                       artifactNumToKeepStr: '1',
                       daysToKeepStr: '30',
                       numToKeepStr: '30'
                     )
        )
    }
    triggers { cron('TZ=Europe/Madrid\n0 0 * * 7') }
    stages {
        stage('Update_Plugins') {
            steps {
                script {
                    def safePluginUpdateModule = load("scripts/jenkins-plugins-uptodate.groovy")
                    safePluginUpdateModule.list_jenkins_plugins("${WORKSPACE}/scripts", "plugins_list_BEFORE-UPDATE_${DATETIME}.txt")
                    (pluginsToReviewManually, pluginsDeprecated) = safePluginUpdateModule.jenkins_safe_plugins_update()
                    safePluginUpdateModule.list_jenkins_plugins("${WORKSPACE}/scripts", "plugins_list_AFTER-UPDATE_${DATETIME}.txt")
                }
            }
        }
    }
    post {
        always {
          script {
              archiveArtifacts "scripts/plugins_list_*_${DATETIME}.txt"
              if (!(pluginsToReviewManually.isEmpty())) {
                withVault([vaultSecrets: secrets]) {
                  discordSend description: "[Jenkins Management] - IMPORTANT!!! The following plugins need to get reviewed and updated manually: ${pluginsToReviewManually}", footer: "", link: env.BUILD_URL, result: currentBuild.currentResult, title: JOB_NAME, webhookURL: "${DISCORD_WEBHOOK}"
                }
              } else if (!(pluginsDeprecated.isEmpty())) {
                withVault([vaultSecrets: secrets]) {
                  discordSend description: "[Jenkins Management] - IMPORTANT!!! The following plugins are deprecated and need to be deleted: ${pluginsDeprecated}", footer: "", link: env.BUILD_URL, result: currentBuild.currentResult, title: JOB_NAME, webhookURL: "${DISCORD_WEBHOOK}"
                }
              }
          }
        }
        failure {
          withVault([vaultSecrets: secrets]) {
            discordSend description: "[Jenkins Management] - Management Task Update Jenkins Plugins failed!", footer: "", link: env.BUILD_URL, result: currentBuild.currentResult, title: JOB_NAME, webhookURL: "${DISCORD_WEBHOOK}"
          }
        }
    }
}
