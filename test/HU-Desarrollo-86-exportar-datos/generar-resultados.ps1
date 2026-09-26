<#
  Genera RESULTADOS.txt con el estado de TODAS las pruebas del backend (no solo las de la HU).

  Lee los informes que deja "mvn test" en target/surefire-reports/TEST-*.xml, asi que primero
  hay que correr la suite:

      mvn test
      powershell -ExecutionPolicy Bypass -File test/HU-Desarrollo-86-exportar-datos/generar-resultados.ps1

  Parametros opcionales:
      -Salida          ruta del .txt (por defecto, RESULTADOS.txt junto a este script)
      -SalidasManuales carpeta con los .log de prueba-manual.sh que se anexan al final
#>
param(
    [string]$Salida = (Join-Path $PSScriptRoot 'RESULTADOS.txt'),
    [string]$SalidasManuales = ''
)

$ErrorActionPreference = 'Stop'
$raiz = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$informes = Join-Path $raiz 'target\surefire-reports'
$xmls = Get-ChildItem -Path $informes -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue
if (-not $xmls) { throw "No hay informes en $informes. Corre 'mvn test' primero." }

# Las pruebas de esta HU, para destacarlas.
$deLaHU = @('LibroDelServicioTest', 'ExportacionServiceTest', 'ExportacionServicioIT',
            'ExportacionConRutaRealIT', 'ExportacionConDatosSimuladosIT', 'CorsIT')

$clases = foreach ($f in $xmls) {
    [xml]$doc = Get-Content -LiteralPath $f.FullName -Encoding UTF8
    $suite = $doc.testsuite
    $casos = @($suite.testcase | ForEach-Object {
        $estado = 'PASS'
        if ($_.failure) { $estado = 'FAIL' } elseif ($_.error) { $estado = 'ERROR' } elseif ($_.skipped -ne $null) { $estado = 'SKIP' }
        $detalle = ''
        if ($_.failure) { $detalle = ($_.failure.message -split "`n")[0] }
        if ($_.error) { $detalle = ($_.error.message -split "`n")[0] }
        [pscustomobject]@{ Nombre = $_.name; Segundos = [double]$_.time; Estado = $estado; Detalle = $detalle }
    })
    [pscustomobject]@{
        Clase   = $suite.name
        Corta   = ($suite.name -split '\.')[-1]
        Casos   = $casos
        Total   = $casos.Count
        Pasaron = @($casos | Where-Object Estado -eq 'PASS').Count
        Fallos  = @($casos | Where-Object { $_.Estado -in 'FAIL', 'ERROR' }).Count
        Omitidas = @($casos | Where-Object Estado -eq 'SKIP').Count
        Segundos = [double]$suite.time
    }
}
$clases = $clases | Sort-Object Clase

$total = ($clases | Measure-Object Total -Sum).Sum
$pasaron = ($clases | Measure-Object Pasaron -Sum).Sum
$fallos = ($clases | Measure-Object Fallos -Sum).Sum
$omitidas = ($clases | Measure-Object Omitidas -Sum).Sum
$veredicto = if ($fallos -eq 0 -and $total -gt 0) { 'TODAS PASARON' } else { 'HAY FALLOS' }
$linea = '=' * 96
$sub = '-' * 96

# "mvn -v" informa el JDK con el que Maven compila de verdad (el de JAVA_HOME), que puede ser
# distinto del "java" del PATH.
$mvnv = try { @(& mvn -v 2>&1) } catch { @() }
$mvn = if ($mvnv.Count) { $mvnv[0] } else { 'desconocido' }
$java = ($mvnv | Where-Object { $_ -match '^Java version' } | Select-Object -First 1)
if (-not $java) { $java = 'desconocido' }
$docker = try { (& docker --version 2>&1 | Select-Object -First 1) } catch { 'desconocido' }

$o = New-Object System.Collections.Generic.List[string]
$o.Add($linea)
$o.Add(' RESULTADOS DE PRUEBAS - Backend EcoRuta (suite completa)')
$o.Add(' HU Desarrollo-86: Exportar los datos del servicio')
$o.Add($linea)
$o.Add((' Generado el       : {0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm')))
$o.Add(' Comando           : mvn test  (todas las clases *Test y *IT + escenarios Cucumber)')
$o.Add(" Java (mvn compila): $java   [el pom fija release 21]")
$o.Add(" Maven            : $mvn")
$o.Add(" Docker           : $docker   (PostGIS 17-3.5 por Testcontainers)")
$o.Add(' Informes fuente   : target/surefire-reports/TEST-*.xml')
$o.Add($linea)
$o.Add('')
$o.Add(" VEREDICTO GLOBAL:  $veredicto")
$o.Add(('   Pruebas ejecutadas : {0}' -f $total))
$o.Add(('   Pasaron            : {0}' -f $pasaron))
$o.Add(('   Fallaron           : {0}' -f $fallos))
$o.Add(('   Omitidas           : {0}' -f $omitidas))
$o.Add(('   Clases de prueba   : {0}' -f $clases.Count))
$o.Add('')

$o.Add($linea)
$o.Add(' A) ESTADO POR CLASE   (marca * = pruebas nuevas o ajustadas por la HU Desarrollo-86)')
$o.Add($linea)
$o.Add(('   {0,-4} {1,-58} {2,6} {3,6} {4,6} {5,8}' -f 'HU', 'Clase', 'Total', 'Pasan', 'Fallan', 'Segundos'))
$o.Add("   $sub")
foreach ($c in $clases) {
    $marca = if ($deLaHU -contains $c.Corta) { '*' } elseif ($c.Clase -like '*PruebasDeAceptacion*') { '*' } else { ' ' }
    $estado = if ($c.Fallos -eq 0) { 'PASS' } else { 'FAIL' }
    $o.Add(('   {0,-4} {1,-58} {2,6} {3,6} {4,6} {5,8:N1}   {6}' -f $marca, $c.Corta, $c.Total, $c.Pasaron, $c.Fallos, $c.Segundos, $estado))
}
$o.Add("   $sub")
$o.Add(('   {0,-4} {1,-58} {2,6} {3,6} {4,6}' -f '', 'TOTAL', $total, $pasaron, $fallos))
$o.Add('')

$fallidos = $clases | ForEach-Object { $c = $_; $c.Casos | Where-Object { $_.Estado -in 'FAIL', 'ERROR' } | ForEach-Object { [pscustomobject]@{ Clase = $c.Corta; Caso = $_ } } }
$o.Add($linea)
$o.Add(' B) PRUEBAS QUE FALLARON')
$o.Add($linea)
if (-not $fallidos) { $o.Add('   Ninguna.') } else {
    foreach ($f in $fallidos) { $o.Add(('   [{0}] {1}.{2}' -f $f.Caso.Estado, $f.Clase, $f.Caso.Nombre)); if ($f.Caso.Detalle) { $o.Add("        $($f.Caso.Detalle)") } }
}
$o.Add('')

$o.Add($linea)
$o.Add(' C) DETALLE DE CADA PRUEBA DE LA HU DESARROLLO-86')
$o.Add($linea)
foreach ($c in ($clases | Where-Object { $deLaHU -contains $_.Corta })) {
    $o.Add((' {0}   ({1} pruebas, {2} fallos)' -f $c.Corta, $c.Total, $c.Fallos))
    foreach ($t in $c.Casos) { $o.Add(('   [{0}] {1}   ({2:N2} s)' -f $t.Estado, $t.Nombre, $t.Segundos)) }
    $o.Add('')
}

$o.Add($linea)
$o.Add(' D) ESCENARIOS GHERKIN (Cucumber, PruebasDeAceptacionTest)')
$o.Add($linea)
$acept = $clases | Where-Object { $_.Clase -like '*PruebasDeAceptacion*' }
if ($acept) {
    $o.Add(('   {0} escenarios, {1} fallos' -f $acept.Total, $acept.Fallos))
    foreach ($t in $acept.Casos) { $o.Add(('   [{0}] {1}' -f $t.Estado, $t.Nombre)) }
} else { $o.Add('   No se encontro el runner de Cucumber en los informes.') }
$o.Add('')

$o.Add($linea)
$o.Add(' E) DETALLE DE TODAS LAS DEMAS PRUEBAS (por clase)')
$o.Add($linea)
foreach ($c in ($clases | Where-Object { $deLaHU -notcontains $_.Corta -and $_.Clase -notlike '*PruebasDeAceptacion*' })) {
    $o.Add((' {0}   ({1} pruebas, {2} fallos)' -f $c.Corta, $c.Total, $c.Fallos))
    foreach ($t in $c.Casos) { $o.Add(('   [{0}] {1}' -f $t.Estado, $t.Nombre)) }
    $o.Add('')
}

if ($SalidasManuales -and (Test-Path $SalidasManuales)) {
    $o.Add($linea)
    $o.Add(' F) PRUEBAS MANUALES END-TO-END (prueba-manual.sh contra la app real)')
    $o.Add($linea)
    foreach ($log in (Get-ChildItem -Path $SalidasManuales -Filter 'manual-*.log' | Sort-Object Name)) {
        $texto = Get-Content -LiteralPath $log.FullName -Encoding UTF8
        $resumen = $texto | Where-Object { $_ -match '^=== resumen' }
        $o.Add((' {0}' -f $log.Name))
        $o.Add(("   {0}" -f ($resumen -join ' ')))
        $o.Add('')
    }
    $o.Add(' Salida completa de cada corrida:')
    $o.Add('')
    foreach ($log in (Get-ChildItem -Path $SalidasManuales -Filter 'manual-*.log' | Sort-Object Name)) {
        $o.Add($sub)
        $o.Add(" >> $($log.Name)")
        $o.Add($sub)
        Get-Content -LiteralPath $log.FullName -Encoding UTF8 | ForEach-Object { $o.Add("   $_") }
        $o.Add('')
    }
}

$o.Add($linea)
$o.Add(" FIN - VEREDICTO: $veredicto  ($pasaron/$total pruebas automaticas)")
$o.Add($linea)

[System.IO.File]::WriteAllLines($Salida, $o, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Escrito $Salida  ->  $veredicto ($pasaron/$total, $fallos fallos)"
