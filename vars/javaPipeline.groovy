// Standard Enterprise Java Microservice Pipeline
// Used by all Java teams at Citizens Bank
// Maintained by DevOps Platform Team

def call(Map config = [:]) {
    // Default configuration
    def defaults = [
        sonarProjectKey: env.JOB_NAME,
        nexusRepo: 'maven-releases',
        skipTests: false,
        runSecurityScans: true,
        deployEnvironment: 'dev'
    ]
    
    // Merge user config with defaults
    def settings = defaults + config
    
    pipeline {
        agent any
        
        tools {
            maven 'Maven'
        }
        
        options {
            timestamps()
            timeout(time: 30, unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '10'))
            disableConcurrentBuilds()
        }
        
        environment {
            APP_NAME = env.JOB_NAME
            BUILD_VERSION = "1.0.${env.BUILD_NUMBER}"
        }
        
        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                    echo "Building ${APP_NAME} #${BUILD_VERSION}"
                }
            }
            
            stage('Build') {
                steps {
                    echo 'Compiling source code...'
                    sh 'mvn clean compile -DskipTests'
                }
            }
            
            stage('Unit Tests') {
                when {
                    expression { !settings.skipTests }
                }
                steps {
                    sh 'mvn test'
                }
                post {
                    always {
                        junit allowEmptyResults: true,
                             testResults: 'target/surefire-reports/*.xml'
                    }
                }
            }
            
            stage('Security Scans') {
                when {
                    expression { settings.runSecurityScans }
                }
                parallel {
                    stage('SonarQube SAST') {
                        steps {
                            echo "Scanning with SonarQube..."
                            withSonarQubeEnv('SonarQube') {
                                sh "mvn sonar:sonar -Dsonar.projectKey=${settings.sonarProjectKey}"
                            }
                        }
                    }
                    
                    stage('Trivy SCA') {
                        steps {
                            echo "Scanning dependencies with Trivy..."
                            sh """
                                trivy fs --severity HIGH,CRITICAL \
                                  --format table \
                                  --exit-code 1 \
                                  pom.xml
                            """
                        }
                    }
                }
            }
            
            stage('Package') {
                steps {
                    sh 'mvn package -DskipTests'
                    echo "Artifact: target/*.war"
                }
            }
            
            stage('Upload to Nexus') {
                steps {
                    echo "Uploading to ${settings.nexusRepo}..."
                    sh 'mvn deploy -DskipTests'
                }
            }
            
            stage('Deploy') {
                steps {
                    echo "Deploying to ${settings.deployEnvironment}"
                    echo "Deployment simulated - would deploy via Helm/K8s"
                }
            }
        }
        
        post {
            success {
                echo "✅ Pipeline SUCCESS: ${APP_NAME} ${BUILD_VERSION}"
            }
            failure {
                echo "❌ Pipeline FAILED: Check console"
            }
        }
    }
}
