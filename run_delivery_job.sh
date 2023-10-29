CURRENT_DATE=`date '+%Y/%m/%d'`
LESSON=$(basename $PWD)
mvn clean package -Dmaven.test.skip=true;
java -jar -Dspring.batch.job.name=deliverPackageJob ./target/linkedIn-batch-*-*-0.0.1-SNAPSHOT.jar "item=croc" "run.date=$CURRENT_DATE" "lesson=$LESSON" type=$1;
read;