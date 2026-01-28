// This pipeline is for product microservice deployment
import com.i27academy.k8s.K8s
def call(Map pipelineParams) {
    // instance 
    K8s k8s = new K8s(this)
    pipeline {
        agent {
            label 'java-slave'
        }

        // tools section 
        tools {
            maven 'maven-3.8.9'
            jdk 'JDK-17'
        }

        // environment 
        environment {

            APPLICATION_NAME = "${pipelineParams.appName}"

            // DOCKER_HUB = "docker.io/i27devopsb7"
            // DOCKER_CREDS = credentials('dockerhub_creds')

            // JFROG details
            JFROG_DOCKER_REGISTRY = "i27k8sb15.jfrog.io"
            JFROG_DOCKER_REPO_NAME = "private-docker"
            JFROG_CREDS = credentials("JFROG_CREDS")

            // Kubernetes Dev cluster details
            DEV_CLUSTER_NAME = "cart-cluster"
            DEV_CLUSTER_ZONE = "us-central1-a"
            DEV_PROJECT_ID = "fluid-analogy-463508-r4"


            // File name for deployments 
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TEST_FILE = "k8s_test.yaml"
            K8S_STAGE_FILE = "k8s_stage.yaml"
            K8S_PROD_FILE = "k8s_prd.yaml"


            // namespace definition 
            DEV_NAMESPACE = "cart-dev-ns"
            TEST_NAMESPACE = "cart-test-ns"
            STAGE_NAMESPACE = "cart-stage-ns"
            PROD_NAMESPACE = "cart-prod-ns"

        }

        // parameters
        parameters {
            choice(name: 'dockerPush',
                choices: 'no\nyes',
                description: 'This will only push the docker image to registry'
            )
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'This will deploy the application to dev environment'
            )
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'This will deploy the application to test environment'
            )
            choice(name: 'deployToStage',
                choices: 'no\nyes',
                description: 'This will deploy the application to stage environment'
            )
            choice(name: 'deployToProd',
                choices: 'no\nyes',
                description: 'This will deploy the application to prod environment'
            )
        }


        stages {
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
            stage ('Deploy To Dev') {
                when {
                    anyOf {
                        expression { 
                            params.deployToDev == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        // image validation
                        //imageValidatiion().call()
                        def docker_image  =  "${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:$GIT_COMMIT"
                        // calling auth login method
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}", "${env.DEV_CLUSTER_ZONE}", "${env.DEV_PROJECT_ID}")
                        imageValidatiion().call()
                        k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                    }
                }
            }
            stage ('Deploy To test') {
            when {
                    anyOf {
                        expression { 
                            params.deployToTest == 'yes'
                        }
                    }
            }
                steps {
                    script{
                        // image validation
                        imageValidatiion().call()
                        dockerDeploy('test', '6232').call()
                    }
                }
            }
            stage ('Deploy To Stage') {
                when {
                    anyOf {
                        expression { 
                            params.deployToStage == 'yes'
                        }
                    }
                    anyOf {
                        branch 'release*'
                        tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP"
                    }
                }
                steps {
                    script{
                        // image validation
                        imageValidatiion().call()
                        dockerDeploy('stage', '7232').call()
                    }
                }
            }
            stage ('Deploy To Prod') {

                // mailing implementaion

                when {
                    anyOf {
                        expression { 
                            params.deployToProd == 'yes'
                        }
                    }
                    anyOf {
                        tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP"
                    }
                }
                steps {
                    timeout(time: 300, unit: 'SECONDS') {
                        input message: "Deploying ${env.APPLICATION_NAME} to production ?", ok: 'yes', submitter: 'i27academy, devlead'
                    }
                    
                    script{
                        dockerDeploy('prod', '8232').call()
                    }
                }
            }
        }
            
    }
}




def buildApp() {
    return {
        echo "********** Building ${env.APPLICATION_NAME} Application *************"
        sh "mvn clean package -DskipTests=true" 
    }
}

def dockerDeploy(envDeploy, port){
    return {
        echo "Deploying to $envDeploy Environment"
            withCredentials([usernamePassword(credentialsId: 'john_docker_vm_creds', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    // some block
                script {
                    try {
                        // stop the container
                        sh "sshpass -p $PASSWORD -v ssh -o StrictHostKeyChecking=no $USERNAME@$docker_vm_ip \"docker stop ${APPLICATION_NAME}-$envDeploy\""

                        // remove the container
                        sh "sshpass -p $PASSWORD -v ssh -o StrictHostKeyChecking=no $USERNAME@$docker_vm_ip \"docker rm ${APPLICATION_NAME}-$envDeploy\"" 
                    }
                    catch(err) {
                        echo "Error caught: $err"
                    }
                    // Creating a container
                    sh "sshpass -p $PASSWORD -v ssh -o StrictHostKeyChecking=no $USERNAME@$docker_vm_ip \"docker run --name ${APPLICATION_NAME}-$envDeploy -p $port:8132 -d ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:$GIT_COMMIT\""
                }
            } 
    }
}



def dockerBuildAndPush() {
    return {
        echo "************* Building the Docker image ***************"
        sh "docker build --no-cache -t ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:$GIT_COMMIT ."
        echo "******************************************** Docker Login *********************************"
        sh "docker login -u ${JFROG_CREDS_USR} -p ${JFROG_CREDS_PSW} i27k8sb15.jfrog.io"
        echo "******************************************** Docker Push *********************************"
         sh "docker push ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:$GIT_COMMIT"
    }
}

def imageValidatiion() {
    return {
        println ("***************************** Attempt to pull the docker image *********************")
        try {
            sh "docker pull ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:$GIT_COMMIT"
            println("********************** Image is Pulled Succesfully *************************")
        }
        catch(Exception e) {
            println("*************** OOPS, the docker image is not available...... So creating the image")
            
            dockerBuildAndPush().call()

        }

    }
}



//  hostport:containerport

// dev > 5232:8232
// test > 6232:8232
// stage > 7232:8232
//prod > 8232:8232
