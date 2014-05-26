#!/bin/bash

if (( $# != 2 )); then
    echo "usage:\ngenerate.sh <path to swift2thrift-generator-cli-N.N.N-standalone.jar> <path to zookeeper-N.N.N.jar>"
    exit
fi

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASE_DIR="$( cd "$DIR/../../../.." && pwd )"

RPC_PATH="$BASE_DIR/curator-x-rpc/target/classes"

CLASSES=""

PROJECTION_CLASSES=""
for f in `ls -m1 $RPC_PATH/org/apache/curator/x/rpc/idl/projection/*.class | xargs -n 1 basename | sed s/\.[^\.]*$//`;
    do
        if [[ $f != *[\$]* ]]; then
            PROJECTION_CLASSES="$PROJECTION_CLASSES $f";
            CLASSES="$CLASSES org.apache.curator.x.rpc.idl.projection.$f";
        fi;
done;

EVENT_CLASSES=""
for f in `ls -m1 $RPC_PATH/org/apache/curator/x/rpc/idl/event/*.class | xargs -n 1 basename | sed s/\.[^\.]*$//`;
    do
        if [[ $f != *[\$]* ]]; then
            EVENT_CLASSES="$EVENT_CLASSES $f";
            CLASSES="$CLASSES org.apache.curator.x.rpc.idl.event.$f";
        fi;
done;

THRIFT_DIR="$BASE_DIR/curator-x-rpc/src/main/thrift"

PATHS="$1:$2"
PATHS="$PATHS:$BASE_DIR/curator-client/target/classes"
PATHS="$PATHS:$BASE_DIR/curator-framework/target/classes"
PATHS="$PATHS:$BASE_DIR/curator-recipes/target/classes"
PATHS="$PATHS:$RPC_PATH"

PACKAGE="org.apache.curator.x.rpc.idl"
PROJECTION_PACKAGE="org.apache.curator.x.rpc.idl.projection"
EVENT_PACKAGE="org.apache.curator.x.rpc.idl.event"

java -cp $PATHS com.facebook.swift.generator.swift2thrift.Main -allow_multiple_packages org.apache.curator -namespace cpp org.apache.curator -out "$THRIFT_DIR/curator.thrift" $CLASSES
