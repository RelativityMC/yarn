pipeline {
    agent any
    options { timestamps() }
    stages {
        stage('SCM-SKIP') {
            steps {
                scmSkip(skipPattern:'.*\\[ci skip\\].*')
            }
        }
        stage('Build') {
            tools {
                jdk "OpenJDK 21"
            }
            environment {
                MAVEN_URL = "https://repo.codemc.io/repository/relativitymc/"
            }
            steps {
                sh 'git fetch --tags'
                sh 'git reset --hard'
                withCredentials([usernamePassword(credentialsId: 'nexus-repository', passwordVariable: 'MAVEN_CRED_PSW', usernameVariable: 'MAVEN_CRED_USR')]) {
                    sh './gradlew build check publish --stacktrace -x javadoc'
                }
            }
            post {
                always {
                    withCredentials([string(credentialsId: 'discord-github-webhook', variable: 'DISCORD_WEBHOOK_URL')]) {
                        discordSend description: "Jenkins Pipeline Build", link: env.BUILD_URL, result: currentBuild.currentResult, title: "${env.JOB_NAME} #${currentBuild.number}", webhookURL: env.DISCORD_WEBHOOK_URL
                    }
                    cleanWs() // clean ws because we have a lot of branches
                }
            }
        }
    }
}
