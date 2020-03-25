# signpackage
Java: Recursively signs certain files, even inside jar files, with "codesign" on macOS.

To notarize Java apps on macOS it is necessary to sign all jar and dylib file in the .app package with the same signature. 
dylib files can even exist inside jar files and have to be signed inside the jar file, too. This program here performs the
codesign process. You can use it e.g. like this:

```
java -jar SignPackage.jar -d appimageoutput -t -r -k "Developer ID Application: John Public (XXXXXXXXXX)" -e "src/main/deploy/package/macosx/MyApp.entitlements"
```

The parameters are:
```
-d The directory that contains the files that have to be codesigned.
-t Set secure timestamp using the codesign timestamp parameter
-r Harden using the codesign runtime parameter
-k Key name
-e Entitlements file
```
