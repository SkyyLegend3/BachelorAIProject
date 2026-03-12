import shutil, os
target = "/Users/joshuavogt/AndroidStudioProjects/BachelorAIProject/composeApp/src/commonMain/kotlin/com/example/bachelor_ai_project/feature"
if os.path.isdir(target):
    shutil.rmtree(target)
    print("Deleted:", target)
else:
    print("Not found:", target)

