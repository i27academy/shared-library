// This pipleine is implemented for all java based applications 
// this should work for eureka, user, product as well...
// import k8s method
import com.i27academy.k8s.K8s

def call(Map pipelineParams) {
    // instance 
    K8s k8s = new K8s(this)
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

            // Kuberentes Dev Cluster Details 
            DEV_CLUSTER_NAME = "i27-cluster"
            DEV_CLUSTER_ZONE = "us-central1-a"
            DEV_PROJECT_ID = "proven-wavelet-481608-k1"
            TEST_PROJECT_ID = "proven-wavelet-481608-k1"
            STAGE_PROJECT_ID = "proven-wavelet-481608-k1"
            PROD_PROJECT_ID = "PROD_PROJECT_ID_HERE"

            // File Names for Deployments
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TEST_FILE = "k8s_test.yaml"
            K8S_STAGE_FILE = "k8s_stage.yaml"
            K8S_PROD_FILE = "k8s_prod.yaml"


            // Namespace Definitions 
            DEV_NAMESPACE = "i27-cart-dev-ns"
            TEST_NAMESPACE = "i27-cart-test-ns"
            STAGE_NAMESPACE = "i27-cart-stage-ns"
            PROD_NAMESPACE = "i27-cart-prod-ns"

            // Chart path details
            HELM_CHART_PATH = "${workpace}/shared-library/chart"
        }
        stages {
            stage ('GitCheckout'){
                steps {
                    script {
                        // Cloning the shared library repo
                        k8s.gitClone()
                    }
                }
            }
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
                        // Defining docker image
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT"
                        // Image Validattion
                        imageValidation().call()
                        // Calling the method and passing the arguments
                        //dockerDeploy('dev', '5761').call()
                        // Calling k8s Auth method
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}", "${env.DEV_CLUSTER_ZONE}", "${env.DEV_PROJECT_ID}")
                        //k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                        k8s.k8sHelmChartDeploy("${env.APPLICATION_NAME}", "dev", "${HELM_CHART_PATH}" , "$GIT_COMMIT", "${env.DEV_NAMESPACE}") 
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
                        // Defining docker image
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT"
                        // Image Validattion
                        imageValidation().call()
                        // Calling k8s Auth method
                        k8s.auth_login("${env.TEST_CLUSTER_NAME}", "${env.TEST_CLUSTER_ZONE}", "${env.TEST_PROJECT_ID}")
                        // Calling K8S Deploy method
                        k8s.k8sdeploy("${env.K8S_TEST_FILE}", docker_image, "${env.TEST_NAMESPACE}")

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
                        // Defining docker image
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT"
                        // Image Validattion
                        imageValidation().call()
                        // Calling k8s Auth method
                        k8s.auth_login("${env.STAGE_CLUSTER_NAME}", "${env.STAGE_CLUSTER_ZONE}", "${env.STAGE_PROJECT_ID}")
                        // Calling K8S Deploy method
                        k8s.k8sdeploy("${env.K8S_STAGE_FILE}", docker_image, "${env.STAGE_NAMESPACE}")
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
                        // Defining docker image
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT"
                        // Calling k8s Auth method
                        k8s.auth_login("${env.PROD_CLUSTER_NAME}", "${env.PROD_CLUSTER_ZONE}", "${env.PROD_PROJECT_ID}")
                        // Calling K8S Deploy method
                        k8s.k8sdeploy("${env.K8S_PROD_FILE}", docker_image, "${env.PROD_NAMESPACE}")
                    }
                }
            }
            stage ('Cleanup') {
                steps {
                    script {
                        echo "Cleaning up the workspace"
                        cleanWs()
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
