#!/usr/bin/env bash
. /etc/profile

SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
APPNAME=OMIAPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
EMAILLIST=cdursun@mcw.edu,mtutaj@mcw.edu
DEVELOPER=cdursun@mcw.edu,mtutaj@mcw.edu

if [ "$SERVER" == "REED" ]; then
  EMAILLIST=cdursun@mcw.edu,mtutaj@mcw.edu,jrsmith@mcw.edu,slaulede@mcw.edu
fi

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar "$@" 2>&1

mailx -s "[$SERVER] OMIA Pipeline Summary" $EMAILLIST < $APPDIR/logs/summary.log

excess_pubmeds_log_file=$APPDIR/logs/excess_pubmeds.log
mismatched_phenes_log_file=$APPDIR/logs/mismatched_phenes.log
not_found_omia_genes_in_rgd_log_file=$APPDIR/logs/not_found_omia_genes_in_rgd.log
warning_log_file=$APPDIR/logs/warning.log

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

last_modified=`stat -c "%Y" $not_found_omia_genes_in_rgd_log_file`
if [ -s "$not_found_omia_genes_in_rgd_log_file" ] && [ $(($current-$last_modified)) -lt 180 ]  #if it has data and new then send in email
then
    mailx -s "[$SERVER] OMIA Pipeline Not Found Ncbi Gene Ids/Symbols in RGD" $EMAILLIST < $not_found_omia_genes_in_rgd_log_file
fi

last_modified=`stat -c "%Y" $warning_log_file`
if [ -s "$warning_log_file" ] && [ $(($current-$last_modified)) -lt 180 ] #if it has data and new then send in email
then
    mailx -s "[$SERVER] OMIA Pipeline Warning " $DEVELOPER < $warning_log_file
fi
