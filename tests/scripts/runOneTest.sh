#!/bin/bash

if [ "$#" -ne 4 ]; then
	echo "Need four parameters: [Spreadsheet file] [Dataset name] [Output folder path] [Threshold]"
    	exit
fi

# JAVA_HOME=/usr/lib/jvm/java-1.11.0-openjdk-amd64
# Only work for MACOS
JAVA_HOME=$(/usr/libexec/java_home)
JAVA_CMD=$JAVA_HOME/bin/java

VM_OPTS="-Xmx8g"

ABS_PATH="$(readlink -f "${BASH_SOURCE}")"
TEST_HOME="$(dirname $ABS_PATH)"

SPREADSHEET=$1
DATASET=$2
OUTPUT_FOLDER=$3
THRESHOLD=$4

TEST_MAIN=org.dataspread.sheetanalyzer.statcollector.SheetStatsCollector

#mvn -q exec:exec -Dexec.executable=echo -Dexec.args="%classpath"
CLASSPATH=/Users/dixin/TACO/taco-code/target/classes:/Users/dixin/.m2/repository/com/github/davidmoten/rtree/0.8.6/rtree-0.8.6.jar:/Users/dixin/.m2/repository/com/github/davidmoten/guava-mini/0.1.1/guava-mini-0.1.1.jar:/Users/dixin/.m2/repository/io/reactivex/rxjava/1.3.8/rxjava-1.3.8.jar:/Users/dixin/.m2/repository/com/google/guava/guava/30.0-jre/guava-30.0-jre.jar:/Users/dixin/.m2/repository/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar:/Users/dixin/.m2/repository/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar:/Users/dixin/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar:/Users/dixin/.m2/repository/org/checkerframework/checker-qual/3.5.0/checker-qual-3.5.0.jar:/Users/dixin/.m2/repository/com/google/errorprone/error_prone_annotations/2.3.4/error_prone_annotations-2.3.4.jar:/Users/dixin/.m2/repository/com/google/j2objc/j2objc-annotations/1.3/j2objc-annotations-1.3.jar:/Users/dixin/.m2/repository/org/apache/poi/poi/5.0.0/poi-5.0.0.jar:/Users/dixin/.m2/repository/org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar:/Users/dixin/.m2/repository/org/slf4j/jcl-over-slf4j/1.7.30/jcl-over-slf4j-1.7.30.jar:/Users/dixin/.m2/repository/commons-codec/commons-codec/1.15/commons-codec-1.15.jar:/Users/dixin/.m2/repository/org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar:/Users/dixin/.m2/repository/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar:/Users/dixin/.m2/repository/com/zaxxer/SparseBitSet/1.2/SparseBitSet-1.2.jar:/Users/dixin/.m2/repository/org/apache/poi/poi-ooxml/5.0.0/poi-ooxml-5.0.0.jar:/Users/dixin/.m2/repository/org/apache/poi/poi-ooxml-lite/5.0.0/poi-ooxml-lite-5.0.0.jar:/Users/dixin/.m2/repository/org/apache/xmlbeans/xmlbeans/4.0.0/xmlbeans-4.0.0.jar:/Users/dixin/.m2/repository/org/apache/commons/commons-compress/1.20/commons-compress-1.20.jar:/Users/dixin/.m2/repository/com/github/virtuald/curvesapi/1.06/curvesapi-1.06.jar:/Users/dixin/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.68/bcpkix-jdk15on-1.68.jar:/Users/dixin/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.68/bcprov-jdk15on-1.68.jar:/Users/dixin/.m2/repository/org/apache/santuario/xmlsec/2.2.1/xmlsec-2.2.1.jar:/Users/dixin/.m2/repository/com/fasterxml/woodstox/woodstox-core/5.2.1/woodstox-core-5.2.1.jar:/Users/dixin/.m2/repository/org/codehaus/woodstox/stax2-api/4.2/stax2-api-4.2.jar:/Users/dixin/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/2.3.2/jakarta.xml.bind-api-2.3.2.jar:/Users/dixin/.m2/repository/jakarta/activation/jakarta.activation-api/1.2.1/jakarta.activation-api-1.2.1.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-all/1.13/batik-all-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-anim/1.13/batik-anim-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-shared-resources/1.13/batik-shared-resources-1.13.jar:/Users/dixin/.m2/repository/xml-apis/xml-apis-ext/1.3.04/xml-apis-ext-1.3.04.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-awt-util/1.13/batik-awt-util-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/xmlgraphics-commons/2.4/xmlgraphics-commons-2.4.jar:/Users/dixin/.m2/repository/commons-io/commons-io/1.3.1/commons-io-1.3.1.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-bridge/1.13/batik-bridge-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-codec/1.13/batik-codec-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-constants/1.13/batik-constants-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-css/1.13/batik-css-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-dom/1.13/batik-dom-1.13.jar:/Users/dixin/.m2/repository/xalan/xalan/2.7.2/xalan-2.7.2.jar:/Users/dixin/.m2/repository/xalan/serializer/2.7.2/serializer-2.7.2.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-ext/1.13/batik-ext-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-extension/1.13/batik-extension-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-gui-util/1.13/batik-gui-util-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-gvt/1.13/batik-gvt-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-i18n/1.13/batik-i18n-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-parser/1.13/batik-parser-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-rasterizer-ext/1.13/batik-rasterizer-ext-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-rasterizer/1.13/batik-rasterizer-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-script/1.13/batik-script-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-slideshow/1.13/batik-slideshow-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-squiggle-ext/1.13/batik-squiggle-ext-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-squiggle/1.13/batik-squiggle-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-svg-dom/1.13/batik-svg-dom-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-svgbrowser/1.13/batik-svgbrowser-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-svggen/1.13/batik-svggen-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-svgpp/1.13/batik-svgpp-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-svgrasterizer/1.13/batik-svgrasterizer-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-swing/1.13/batik-swing-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-transcoder/1.13/batik-transcoder-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-util/1.13/batik-util-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-ttf2svg/1.13/batik-ttf2svg-1.13.jar:/Users/dixin/.m2/repository/org/apache/xmlgraphics/batik-xml/1.13/batik-xml-1.13.jar:/Users/dixin/.m2/repository/de/rototor/pdfbox/graphics2d/0.30/graphics2d-0.30.jar:/Users/dixin/.m2/repository/org/apache/pdfbox/pdfbox/2.0.22/pdfbox-2.0.22.jar:/Users/dixin/.m2/repository/org/apache/pdfbox/fontbox/2.0.22/fontbox-2.0.22.jar

$JAVA_CMD $VM_OPTS \
       	-classpath $CLASSPATH \
	    $TEST_MAIN \
	    "$SPREADSHEET" \
        "$DATASET" \
       	"$OUTPUT_FOLDER" \
       	"$THRESHOLD"

