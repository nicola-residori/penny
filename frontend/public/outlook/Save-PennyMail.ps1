# Penny CRM - Outlook classico su Windows. Nessuna password o chiave su disco.
# Eseguire con Windows PowerShell 5.1 in modalita STA, dopo aver verificato lo script.
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$script:BaseUrl = 'https://grezzano.homepc.it:9091'
$script:Outlook = $null
$script:Candidates = @()
$script:Messages = @()
function Invoke-Penny($Path, $Payload) {
    $json = ConvertTo-Json -InputObject $Payload -Depth 10 -Compress
    try { Invoke-RestMethod -Uri ($script:BaseUrl + '/api/outlook/bridge/' + $Path) -Method Post -Headers @{Authorization=('Bearer ' + $token.Text.Trim())} -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) }
    catch { throw ('Penny non ha accettato la richiesta. Verifica connessione, chiave, permessi e limiti (20 MB totali, 10 MB per allegato). HTTP: ' + $_.Exception.Response.StatusCode) }
}
function Get-Smtp($Entry) {
    if ($null -eq $Entry) { return '' }
    try { $smtp = $Entry.PropertyAccessor.GetProperty('http://schemas.microsoft.com/mapi/proptag/0x39FE001E'); if ($smtp -and $smtp.Contains('@')) { return $smtp } } catch {}
    try { $user = $Entry.GetExchangeUser(); if ($user -and $user.PrimarySmtpAddress) { return $user.PrimarySmtpAddress } } catch {}
    try { if ($Entry.Address -and $Entry.Address.Contains('@')) { return $Entry.Address } } catch {}
    return ''
}
function Add-ConversationItems($Items, $Conversation, $Target, $Seen) {
    for ($i=1; $i -le $Items.Count; $i++) {
        $item = $Items.Item($i)
        if ($item.Class -eq 43 -and -not $Seen.Contains($item.EntryID)) {
            [void]$Seen.Add($item.EntryID); [void]$Target.Add($item)
            if ($Target.Count -gt 100) { throw 'Conversazione oltre 100 messaggi: salva una selezione piu piccola.' }
            $children = $Conversation.GetChildren($item)
            if ($null -ne $children -and $children.Count -gt 0) { Add-ConversationItems $children $Conversation $Target $Seen }
        }
    }
}
function Get-Payload($Mail) {
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ('penny-mail-' + [Guid]::NewGuid().ToString('N'))
    [void](New-Item -ItemType Directory -Path $tmp)
    try {
        $original = Join-Path $tmp 'original.msg'; $Mail.SaveAs($original, 9)
        $bytes = [IO.File]::ReadAllBytes($original); $size = $bytes.Length
        $attachments = @()
        for ($i=1; $i -le $Mail.Attachments.Count; $i++) {
            $a = $Mail.Attachments.Item($i)
            if ($a.Size -gt 10485760) { throw ('Allegato oltre 10 MB: ' + $a.FileName) }
            $path = Join-Path $tmp ($i.ToString() + '.bin'); $a.SaveAsFile($path)
            $file = [IO.File]::ReadAllBytes($path); $size += $file.Length
            $attachments += @{filename=[string]$a.FileName;contentBase64=[Convert]::ToBase64String($file);inlineImage=$false}
        }
        if ($size -gt 20971520) { throw 'Messaggio e allegati superano 20 MB. Nessun salvataggio per questo messaggio.' }
        $recipients = @()
        for ($i=1; $i -le $Mail.Recipients.Count; $i++) { $r=Get-Smtp ($Mail.Recipients.Item($i).AddressEntry); if ($r) { $recipients += $r } }
        $sender = Get-Smtp $Mail.Sender
        if (-not $sender -and $Mail.SenderEmailAddress -and $Mail.SenderEmailAddress.Contains('@')) { $sender=$Mail.SenderEmailAddress }
        if (-not $sender) { throw 'Impossibile determinare l indirizzo SMTP del mittente.' }
        $internetId = $null
        try { $internetId=$Mail.PropertyAccessor.GetProperty('http://schemas.microsoft.com/mapi/proptag/0x1035001F') } catch {}
        $mailbox=Get-Smtp ($script:Outlook.Session.CurrentUser.AddressEntry)
        if (-not $mailbox) { throw 'Impossibile determinare l indirizzo della casella Outlook.' }
        $date = $Mail.SentOn; if ($date.Year -lt 1900) { $date=$Mail.CreationTime }
        return @{customerId=[long]$script:Candidates[$customer.SelectedIndex].id;mailbox=$mailbox;itemId=[string]$Mail.EntryID;internetMessageId=$internetId;conversationId=[string]$Mail.ConversationID;subject=[string]$Mail.Subject;sender=$sender;recipients=@($recipients);sentAt=$date.ToUniversalTime().ToString('o');bodyText=[string]$Mail.Body;originalBase64=[Convert]::ToBase64String($bytes);originalKind='MSG';attachments=@($attachments)}
    } finally { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
}
$form = New-Object System.Windows.Forms.Form
$form.Text='Salva in Penny - Outlook classico'; $form.Size=New-Object System.Drawing.Size(610,540); $form.StartPosition='CenterScreen'; $form.Font=New-Object System.Drawing.Font('Segoe UI',10); $form.FormBorderStyle='FixedDialog'; $form.MaximizeBox=$false
function Label-At($Text,$Y) { $l=New-Object System.Windows.Forms.Label; $l.Text=$Text; $l.Location=New-Object System.Drawing.Point(20,$Y); $l.Size=New-Object System.Drawing.Size(550,25); $form.Controls.Add($l) }
Label-At 'Chiave personale (creala in Penny > Cliente > Email > Collega Outlook)' 18
$token=New-Object System.Windows.Forms.TextBox; $token.Location=New-Object System.Drawing.Point(20,45); $token.Size=New-Object System.Drawing.Size(550,30); $token.UseSystemPasswordChar=$true; $form.Controls.Add($token)
$thread=New-Object System.Windows.Forms.CheckBox; $thread.Text='Includi la conversazione disponibile in Outlook'; $thread.Location=New-Object System.Drawing.Point(20,90); $thread.Size=New-Object System.Drawing.Size(550,30); $form.Controls.Add($thread)
$prepare=New-Object System.Windows.Forms.Button; $prepare.Text='Leggi messaggio selezionato'; $prepare.Location=New-Object System.Drawing.Point(20,130); $prepare.Size=New-Object System.Drawing.Size(550,38); $form.Controls.Add($prepare)
Label-At 'Cerca cliente / prospect per nome (facoltativo)' 185
$query=New-Object System.Windows.Forms.TextBox; $query.Location=New-Object System.Drawing.Point(20,212); $query.Size=New-Object System.Drawing.Size(380,30); $query.MaxLength=100; $form.Controls.Add($query)
$search=New-Object System.Windows.Forms.Button; $search.Text='Cerca'; $search.Location=New-Object System.Drawing.Point(415,209); $search.Size=New-Object System.Drawing.Size(155,36); $form.Controls.Add($search)
Label-At 'Conferma sotto quale cliente salvare tutti i messaggi' 260
$customer=New-Object System.Windows.Forms.ComboBox; $customer.DropDownStyle='DropDownList'; $customer.Location=New-Object System.Drawing.Point(20,290); $customer.Size=New-Object System.Drawing.Size(550,30); $form.Controls.Add($customer)
$save=New-Object System.Windows.Forms.Button; $save.Text='Salva in Penny'; $save.Location=New-Object System.Drawing.Point(20,345); $save.Size=New-Object System.Drawing.Size(550,42); $save.Enabled=$false; $form.Controls.Add($save)
$status=New-Object System.Windows.Forms.Label; $status.Text='Apri Outlook classico e seleziona una mail. Nessun invio automatico.'; $status.Location=New-Object System.Drawing.Point(20,405); $status.Size=New-Object System.Drawing.Size(550,80); $form.Controls.Add($status)
$script:FindCustomers = {
    if (-not $script:Messages.Count) { throw 'Leggi prima un messaggio.' }
    $emails=@(); foreach($m in $script:Messages) { $sender=Get-Smtp $m.Sender; if($sender) { $emails += $sender }; for($i=1;$i -le $m.Recipients.Count;$i++){ $address=Get-Smtp ($m.Recipients.Item($i).AddressEntry); if($address){$emails += $address} } }
    $self=Get-Smtp ($script:Outlook.Session.CurrentUser.AddressEntry)
    $emails=@($emails | Where-Object { $_ -ne $self } | Select-Object -Unique)
    $script:Candidates=@(Invoke-Penny 'customers' @{emails=$emails;q=$query.Text})
    $customer.Items.Clear(); foreach($c in $script:Candidates) { [void]$customer.Items.Add($c.name + $(if($c.matched){' - email corrispondente'}else{''})) }
    $matches=@($script:Candidates | Where-Object {$_.matched}); if($matches.Count -eq 1){for($i=0;$i -lt $script:Candidates.Count;$i++){if($script:Candidates[$i].id -eq $matches[0].id){$customer.SelectedIndex=$i}}}
    $save.Enabled=$customer.SelectedIndex -ge 0
}
$prepare.Add_Click({ try {
    if(-not $token.Text.Trim()){throw 'Incolla prima la chiave personale.'}
    $script:Outlook=New-Object -ComObject Outlook.Application
    $selection=$script:Outlook.ActiveExplorer().Selection
    if($selection.Count -ne 1 -or $selection.Item(1).Class -ne 43){throw 'Seleziona esattamente un messaggio in Outlook classico.'}
    $item=$selection.Item(1); $script:Messages=@($item)
    if($thread.Checked){$conversation=$item.GetConversation(); if($null -eq $conversation){throw 'Conversazione non disponibile: deseleziona la casella per salvare il solo messaggio.'}; $list=New-Object System.Collections.ArrayList; $seen=New-Object 'System.Collections.Generic.HashSet[string]'; Add-ConversationItems ($conversation.GetRootItems()) $conversation $list $seen; $script:Messages=@($list.ToArray())}
    $status.Text=($script:Messages.Count.ToString()+' messaggi pronti. Verifica il cliente prima di salvare.'); & $script:FindCustomers
} catch { $script:Messages=@();$save.Enabled=$false;[void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'Penny') } })
$search.Add_Click({try{& $script:FindCustomers}catch{[void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'Penny')}})
$customer.Add_SelectedIndexChanged({$save.Enabled=$customer.SelectedIndex -ge 0 -and $script:Messages.Count -gt 0})
$save.Add_Click({
    if([System.Windows.Forms.MessageBox]::Show(('Salvare '+$script:Messages.Count+' messaggi sotto '+$script:Candidates[$customer.SelectedIndex].name+'?'),'Conferma','YesNo') -ne 'Yes'){return}
    $save.Enabled=$false;$prepare.Enabled=$false;$search.Enabled=$false;$form.UseWaitCursor=$true;$saved=0;$duplicate=0
    try { foreach($m in $script:Messages){$payload=Get-Payload $m;$result=Invoke-Penny 'messages' $payload;if($result.duplicate){$duplicate++}else{$saved++}};$status.Text="Salvate $saved email; $duplicate gia presenti. Originali MSG e allegati inclusi." }
    catch { $status.Text="Salvate $saved email; $duplicate gia presenti. Puoi riprovare senza duplicati.";[void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'Penny') }
    finally {$form.UseWaitCursor=$false;$save.Enabled=$true;$prepare.Enabled=$true;$search.Enabled=$true}
})
$thread.Add_CheckedChanged({$script:Messages=@();$save.Enabled=$false;$status.Text='Scelta cambiata: premi Leggi messaggio selezionato.'})
[void]$form.ShowDialog();$token.Text='';$form.Dispose()
