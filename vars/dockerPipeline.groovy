// This pipleine is implemented for all java based applications 
// this should work for eureka, user, product as well...

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'k8s-slave'
        }
        tools {
            maven 'Maven-3.8.9'
            jdk 'JDK-17'
        }
        parameters {
            choice(name: 'buildOnly',
                choices: 'no\nyes',
                description: 'This will only build the application'
            )
            choice(name: 'dockerPush',
                choices: 'no\nyes',
                description: 'This will build and push image to registry'
            )
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'This will deploy the application to dev environment'
            )
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'This will deploy the application to Test environment'
            )
            choice(name: 'deployToStage',
                choices: 'no\nyes',
                description: 'This will deploy the application to Stage environment'
            )
            choice(name: 'deployToProd',
                choices: 'no\nyes',
                description: 'This will deploy the application to Prod environment'
            )
        }
        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            //APPLICATION_NAME = "eureka"
            SONAR_URL = "http://34.48.167.83:9000"
            SONAR_TOKEN = credentials('sonar_creds')
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()

            // Docker hub details 
            DOCKER_HUB = "docker.io/i27devopsb8"
            DOCKER_CREDS = credentials("dockerhub_creds")
            //JFROG_DOCKER_REPO = "i27.jfrog.io"

        }
        stages {
            stage ('build'){
                when {
                    anyOf {
                        expression {
                            params.buildOnly == 'yes'
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        buildApp().call()
                    }
                    
                }
            }
            // stage ('sonarqube') {
            //     steps {
            //         echo "Starting Sonar Scans"
            //         withSonarQubeEnv('SonarQube'){
            //             sh """
            //             mvn clean verify sonar:sonar \
            //                 -Dsonar.projectKey=i27-eureka \
            //                 -Dsonar.host.url=${env.SONAR_URL} \
            //                 -Dsonar.login=${env.SONAR_TOKEN}                  
            //             """
            //         }
            //         timeout (time: 2, unit: 'MINUTES'){
            //             script {
            //                 waitForQualityGate abortPipeline: true
            //             }
            //         }
    
            //     }
            // }
            stage ('DockerBuildAndPush') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        dockerBuildAndPush().call()
                    }
                }
            }
            stage ('DeployToDev') {
                when {
                    anyOf {
                        expression {
                            params.deployToDev == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        // Image Validattion
                        imageValidation().call()
                        // Calling the method and passing the arguments
                        dockerDeploy('dev', '5761').call()
                    }
                }
            }
            stage ('DeployToTest') {
                when {
                    anyOf {
                        expression {
                            params.deployToTest == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        // Image Validattion
                        imageValidation().call()
                        // Calling the method and passing the arguments
                        dockerDeploy('test', '6761').call()
                    }
                }
            }
            stage ('DeployToStage') {
                when {
                    anyOf {
                        expression {
                            params.deployToStage == 'yes'
                        }
                    }
                    anyOf {
                        branch 'release*'
                        tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP"
                        // v1.2.3
                    }
                }
                steps {
                    script {
                        // Image Validattion
                        imageValidation().call()
                        // Calling the method and passing the arguments
                        dockerDeploy('stage', '7761').call()
                    }
                }
            }
            stage ('DeployToProd') {
                when {
                    anyOf {
                        expression {
                            params.deployToProd == 'yes'
                        }
                    }
                    anyOf {
                        tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP"
                        // v1.2.3
                    }
                }
                steps {
                    timeout(time: 300, unit: 'SECONDS'){
                        input message: "Deploying Eureka to Production ?", ok: 'yes', submitter: 'i27academy, sreuser'
                    }
                    script {
                        // Calling the method and passing the arguments
                        dockerDeploy('prod', '8761').call()
                    }
                }
            }

        }
    }
}
// Build the application 
def buildApp() {
    return {
        echo "Building ${env.APPLICATION_NAME} Application"
        sh "mvn package -DskipTests=true"
    }
}

// Docker Build and Push method 
def dockerBuildAndPush() {
    return {
        echo "**** Building Docker Images ******"
        sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT ./.cicd"
        echo "******************************** Docker Login ********************************"
        sh "docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}"
        echo "******************************** Docker Push ********************************"
        sh "docker push ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT"
    }
}


// Deploy to Container Method
def dockerDeploy(envDeploy, port) {
    return {
        echo "Deploying to $envDeploy environment" 
        script {
            try {
            // Stop the container 
            sh "docker stop ${env.APPLICATION_NAME}-$envDeploy"

            // Remove the Container 
            sh "docker rm ${env.APPLICATION_NAME}-$envDeploy"
            }
            catch(err) {
                echo "Error Caught : $err"
            }
            // Creating a Container
            sh "docker run --name ${env.APPLICATION_NAME}-$envDeploy -d -p $port:8761 -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT"
        }

    }
}


// Image Validation 

def imageValidation(){
    return {
        try {
            sh "docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT"
            println("************************* Image is Pulled Succesfully *************************")
        }
        catch(Exception e){
            println ("*********************** OOPS , The image is not availablem ...... So creating it ")
            buildApp().call()
            dockerBuildAndPush().call()
        }
    }
}
