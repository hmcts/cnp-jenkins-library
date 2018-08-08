

#$env:Path = $env:Path + ";C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\MSBuild\15.0\Bin"


$Path = Resolve-Path **\\**.sln

if (Test-Path $Path) {

    try {
        $proc = Start-Process -NoNewWindow -PassThru -FilePath msbuild -ArgumentList "$Path /t:rebuild  /fileLogger /p:Configuration=Release /p:ProductVersion=1.0.0.${env.BUILD_NUMBER}"
        $proc.WaitForExit()    
    }
    catch {

        throw $Error
    
    }
    if ($LASTEXITCODE -gt 0) {
        Write-Output "Build failed!"
        Break
    }


}
else {
    Write-Output "Solution not found!"
    break 
}




<#


$proc = Start-Process -NoNewWindow -PassThru -FilePath msbuild -ArgumentList "$Path /t:go /t:rebuild  /fileLogger /p:Configuration=Release /p:ProductVersion=1.0.0.${env.BUILD_NUMBER}"


msbuild $Path /t:rebuild /fileLogger /p:Configuration=Release /p:ProductVersion=1.0.0 
$proc.WaitForExit()


$LASTEXITCODE

#>