$ErrorActionPreference = "Stop"
$version = "3.9.16"
$baseDir = Join-Path $env:USERPROFILE ".m2\wrapper\dists\apache-maven-$version"
$mavenHome = Join-Path $baseDir "apache-maven-$version"
$mavenExe = Join-Path $mavenHome "bin\mvn.cmd"
$archive = Join-Path $baseDir "apache-maven-$version-bin.zip"
$url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"

if (-not (Test-Path $mavenExe)) {
    New-Item -ItemType Directory -Force -Path $baseDir | Out-Null
    Invoke-WebRequest -Uri $url -OutFile $archive
    Expand-Archive -Path $archive -DestinationPath $baseDir -Force
}

& $mavenExe @args
exit $LASTEXITCODE
