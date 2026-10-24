Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$downloadUrl = "https://adoptium.net/temurin/releases/?version=11"
$form = New-Object System.Windows.Forms.Form
$form.Text = "Java required"
$form.ClientSize = New-Object System.Drawing.Size(440, 165)
$form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::FixedDialog
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.ShowInTaskbar = $true

$message = New-Object System.Windows.Forms.Label
$message.Location = New-Object System.Drawing.Point(18, 18)
$message.Size = New-Object System.Drawing.Size(404, 55)
$message.Text = "UT99 Server Browser requires Java 11 or newer. Please install a JDK; a JRE alone is not sufficient."
$form.Controls.Add($message)

$downloadButton = New-Object System.Windows.Forms.Button
$downloadButton.Location = New-Object System.Drawing.Point(18, 100)
$downloadButton.Size = New-Object System.Drawing.Size(205, 32)
$downloadButton.Text = "Download Java 11+ JDK"
$downloadButton.Add_Click({
    try {
        Start-Process $downloadUrl
    } catch {
        [System.Windows.Forms.MessageBox]::Show(
            "Could not open the download page. Visit $downloadUrl",
            "Unable to open browser",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
})
$form.Controls.Add($downloadButton)

$closeButton = New-Object System.Windows.Forms.Button
$closeButton.Location = New-Object System.Drawing.Point(342, 100)
$closeButton.Size = New-Object System.Drawing.Size(80, 32)
$closeButton.Text = "Close"
$closeButton.DialogResult = [System.Windows.Forms.DialogResult]::OK
$form.Controls.Add($closeButton)
$form.AcceptButton = $closeButton

[void]$form.ShowDialog()
