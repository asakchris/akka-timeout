# SPVDE POC - Akka Actors with Akka Streams

## Build

### SBT build
To build via sbt, you will need to have SBT installed at the command line.  Alternatively, in IntelliJ, you can use SBT shell.

#### Commands
SPVDE uses native packager, whose docs can be found [here](https://github.com/sbt/sbt-native-packager).  There are many options to build, but these will be the most useful ones:

* `sbt stage`
    * Stages the universal executables, without zipping them up.  GIves you access to run the shell scripts to test a package.
* `sbt universal:packageBin`
    * Docs [here](https://www.scala-sbt.org/sbt-native-packager/formats/universal.html)
    * Creates a portable zip file.  Inside the zip are several scripts (Linux and Windows) in the "bin" directory, and all necessary .jar files in "lib".
* `sbt docker:publishLocal`
    * Docs [here](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html#build)
    * Deploys to a local Docker repository, for testing.
    * Can use docker:publish with proper configuration to deploy directly to a shared Docker Repository.  E.g., Artifactory.
* `sbt docker:publish`
    * Requires you to be logged in via `docker login`.
    * Will publish to the URL specified in build.sbt's `dockerRepository` setting.
    * Will publish as `name:version` specified in build.sbt.

### Docker Commands
After publishing locally, you are able to do multiple things to test the container locally, and on a development server.
```
docker images
```
list all the images available locally

```
docker tag image-name:version 699183880494.dkr.ecr.us-east-1.amazonaws.com/spvde/image-name
```
Tag a local image version, in preparation to push to a repository

```
docker push 699183880494.dkr.ecr.us-east-1.amazonaws.com/spvde/image-name
```
Push the image you just tagged to the Amazon ECR repo, under namespace spvde, and image name image-name.  Requires docker login.

>Note:
>* -d  - run as a daemon in the background.
>* -p \<extport>:\<dockerPort> - Map machine's external port to the docker internal port.
>* --name <name> - Names your image, so you can refer to a specific name for the running container.
```
docker run -d -p 9999:8558 <imageID/tag> -Dconfig.resource=/dev.conf
```
Runs the event processor normally.  You can pass -D options after the docker command, and they will be passed directly to the spvde-system-main shell script.

```
docker run -d -p 9999:8558 --entrypoint "/opt/docker/bin/constituent-consumemr" <image/tag> -Dconfig.resource=/dev.conf
```
Runs the constituent consumer script inside the event-processor docker image.  You can specify any script with --entrypoint "myscript".

Note, any options that you wish to pass to the script will have to go at the end of the command.

#### Docker commands on an EC2 instance
All of the above commands

#### Telemetry
Docs can be found [here](https://developer.lightbend.com/docs/reactive-platform/2.0/setup/setup-sbt.html).

To build with telemetry, you will need to put a file with your Lightbend credentials in your userhome\.lightbend\ folder.  The filename should be "user.credentials", and should have the following format:

```
realm = Bintray
ho
user = <username>@lightbend
password = <hashed password from credentials page>
```