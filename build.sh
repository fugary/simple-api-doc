cd simple-api-doc-ui
npm install
npm run build
cd ..
rm -rf simple-api-doc/src/main/resources/static/
cp -R simple-api-doc-ui/dist simple-api-doc/src/main/resources/static
mvn clean install -Dmaven.test.skip=true -Pproduction
