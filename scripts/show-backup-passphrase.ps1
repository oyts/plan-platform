param([Parameter(Mandatory=$true)][string]$PassphraseFile)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$owner=[Security.Principal.WindowsIdentity]::GetCurrent()
if($owner.Name -match 'CodexSandbox'){throw 'View the backup passphrase only in your own local Windows session.'}
$path=[IO.Path]::GetFullPath($PassphraseFile)
$allowed=[IO.Path]::GetFullPath((Join-Path $projectRoot '.desktop-tools/signing-backups/private'))+[IO.Path]::DirectorySeparatorChar
if(-not $path.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -gt 4KB){throw 'Unexpected protected passphrase path.'}
Add-Type -AssemblyName System.Security
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$bytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($path),[Text.Encoding]::UTF8.GetBytes('PlanCabin.BackupPassphrase.v1'),[Security.Cryptography.DataProtectionScope]::CurrentUser)
try{
    $secret=[Text.Encoding]::UTF8.GetString($bytes)
    $form=New-Object Windows.Forms.Form;$form.Text='Plan Cabin backup passphrase';$form.Width=700;$form.Height=280;$form.StartPosition='CenterScreen';$form.FormBorderStyle='FixedDialog';$form.MaximizeBox=$false;$form.TopMost=$true
    $label=New-Object Windows.Forms.Label;$label.Text='Save this passphrase separately in your password manager. Do not send it to chat or store it with the encrypted backup.';$label.SetBounds(20,20,640,46);$form.Controls.Add($label)
    $box=New-Object Windows.Forms.TextBox;$box.ReadOnly=$true;$box.Text=$secret;$box.Font=New-Object Drawing.Font('Consolas',12);$box.SetBounds(20,76,640,32);$form.Controls.Add($box)
    $copy=New-Object Windows.Forms.Button;$copy.Text='Copy passphrase';$copy.SetBounds(20,122,180,32);$copy.Add_Click({[Windows.Forms.Clipboard]::SetText($secret)});$form.Controls.Add($copy)
    $check=New-Object Windows.Forms.CheckBox;$check.Text='I saved the passphrase separately, not with the backup file.';$check.SetBounds(20,167,640,24);$form.Controls.Add($check)
    $done=New-Object Windows.Forms.Button;$done.Text='Saved - continue';$done.Enabled=$false;$done.SetBounds(450,204,210,32);$check.Add_CheckedChanged({$done.Enabled=$check.Checked});$done.Add_Click({$form.DialogResult=[Windows.Forms.DialogResult]::OK;$form.Close()});$form.Controls.Add($done)
    if($form.ShowDialog() -ne [Windows.Forms.DialogResult]::OK){throw 'Separate passphrase storage was not confirmed.'}
}finally{
    if($secret -and [Windows.Forms.Clipboard]::ContainsText() -and [Windows.Forms.Clipboard]::GetText() -ceq $secret){[Windows.Forms.Clipboard]::Clear()}
    [Array]::Clear($bytes,0,$bytes.Length);$secret=$null;if($box){$box.Text=''};if($form){$form.Dispose()}
}
