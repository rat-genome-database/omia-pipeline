#!/usr/bin/env bash
. /etc/profile

SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
APPNAME="omia-pipeline"
APPDIR=/home/rgddata/pipelines/$APPNAME
EMAILLIST=mtutaj@mcw.edu

if [ "$SERVER" == "REED" ]; then
  EMAILLIST=mtutaj@mcw.edu,jrsmith@mcw.edu,slaulede@mcw.edu
fi

cd $APPDIR

java -Xmx128G -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/$APPNAME.jar "$@" 2>&1

mailx -s "[$SERVER] OMIA Pipeline Summary" $EMAILLIST < $APPDIR/logs/summary.log

excess_pubmeds_log_file=$APPDIR/logs/excess_pubmeds_summary.log
mismatched_phenes_log_file=$APPDIR/logs/mismatched_phenes_summary.log

current=`date +%s`

last_modified=`stat -c "%Y" $excess_pubmeds_log_file`
if [ -s "$excess_pubmeds_log_file" ] && [ $(($current-$last_modified)) -lt 180 ] #if it has data and new then send in email
then
    mailx -s "[$SERVER] OMIA Pipeline Excess Number of Pubmeds" $EMAILLIST < $excess_pubmeds_log_file
fi

last_modified=`stat -c "%Y" $mismatched_phenes_log_file`
if [ -s "$mismatched_phenes_log_file" ] && [ $(($current-$last_modified)) -lt 180 ] #if it has data and new then send in email
then
    mailx -s "[$SERVER] OMIA Pipeline Mismatched Phene Terms" $EMAILLIST < $mismatched_phenes_log_file
fi
