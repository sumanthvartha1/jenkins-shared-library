pipeline {
    agent any
    
    tools {
        maven 'Maven'
    }
    
    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }
    
    environment {
        APP_NAME = 'payment-service'
    }
    
    stages {
        stage('1. Checkout') {
            steps {
                checkout scm
                echo "Building ${APP_NAME} #${env.BUILD_NUMBER}"
            }
        }
        
        stage('2. Build') {
            steps {
                echo 'Compiling source code...'
                sh 'mvn clean compile'
            }
        }
        
        stage('3. Unit Tests') {
            steps {
                echo 'Running unit tests...'
                sh 'mvn test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }
        
        stage('4. Parallel Security Scans') {
            parallel {
                stage('4a. SonarQube SAST') {
                    steps {
                        echo 'Running SonarQube code analysis...'
                        withSonarQubeEnv('SonarQube') {
                            sh 'mvn sonar:sonar'
                        }
                        echo 'SonarQube analysis submitted. Check dashboard for results.'
                    }
                }
                
                stage('4b. Trivy SCA Scan') {
                    steps {
                        echo 'Scanning dependencies with Trivy...'
                        sh '''
                            trivy fs --severity HIGH,CRITICAL \
                              --format table \
                              --exit-code 1 \
                              pom.xml
                        '''
                        echo 'Trivy scan complete - no critical vulnerabilities found'
                    }
                }
            }
        }
        
        stage('5. Package') {
            steps {
                echo 'Creating WAR file...'
                sh 'mvn package -DskipTests'
                echo 'Artifact created: target/payment-service-1.0.0.war'
            }
        }
        
        stage('6. Upload to Nexus') {
            steps {
                echo 'Uploading artifact to Nexus Repository...'
                sh 'mvn deploy -DskipTests'
                echo 'Artifact uploaded to Nexus'
            }
        }
    }
    
    post {
        success {
            echo "✅ PIPELINE SUCCESS"
            echo "SonarQube: http://<EC2-IP>:9000/dashboard?id=payment-service"
            echo "Nexus: http://<EC2-IP>:8081"
        }
        failure {
            echo "❌ Pipeline FAILED"
        }
    }
}

