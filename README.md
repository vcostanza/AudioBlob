![AudioBlob](src/software/blob/audio/ui/editor/res/icons/audioblob.png)

AudioBlob is a simple digital audio workstation (DAW) I created while experimenting with sound in order to make
sample-based music.

## Features

- Piano roll
- Sample-based instruments
- Instrument generator using a seed WAV file
- Pitch detection and conversion to modifiable "curves"
- Hand drawn pitch curves

## Dependencies

[**Java 8 JDK**](http://openjdk.java.net/projects/jdk8/)

[**BlobView**](https://github.com/vcostanza/BlobView)

[**SBSMS**](https://github.com/claytonotey/libsbsms)

[**JSON in Java**](https://mvnrepository.com/artifact/org.json/json)

## Compiling

It's recommended to use [**IntelliJ IDEA**](https://www.jetbrains.com/idea/) to build the project.

The main dependency [**BlobView**](https://github.com/vcostanza/BlobView) is used to render the GUI. It can be included
in the project as either a JAR or module. If you would like to compile from source and have [**Git**](https://git-scm.com/) installed run:

```
git clone https://github.com/vcostanza/BlobView
```

AudioBlob also requires a JNI library to utilize the features of the SBSMS library.
To build the JNI library open the `jni` directory and run `build.sh`
This will automatically download and build the SBSMS shared library in addition to the JNI wrapper library.

Note: You may need to set `JDK_HOME` to successfully build the JNI library:

```
export JDK_HOME=<location of your Java 8 JDK installation>
```

The JSON in Java library should be automatically imported by Maven ([**org.json:json:20210307**](https://mvnrepository.com/artifact/org.json/json/20210307)).

## Platform Support

Currently only tested on Linux (Debian).