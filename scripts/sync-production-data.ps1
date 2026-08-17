[CmdletBinding()]
param(
    [string]$ProductionApiBase = 'https://huanwoshidai.com.cn/app-api',
    [long]$TenantId = 201,
    [string]$DockerContainer = 'campus-mysql',
    [string]$Database = 'SuperCampus',
    [string]$MySqlUser = 'root',
    [string]$MySqlPassword = $env:CAMPUS_LOCAL_DB_PASSWORD
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($MySqlPassword)) {
    throw 'Set CAMPUS_LOCAL_DB_PASSWORD before synchronizing production data into the local database.'
}

function Find-DockerExecutable {
    $command = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidates = @(
        'C:\Program\resources\bin\docker.exe',
        'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    throw 'Docker CLI was not found. Start Docker Desktop and try again.'
}

function ConvertTo-SqlString([AllowNull()][object]$Value) {
    if ($null -eq $Value) {
        return 'NULL'
    }
    $text = [string]$Value
    # MySQL treats backslashes as escape characters in string literals. Preserve
    # JSON escapes such as \u0026 so the JSON parser can restore signed URL '&'.
    $text = $text.Replace('\', '\\').Replace("'", "''").Replace("`0", '').Replace("`r", ' ').Replace("`n", ' ')
    return "'$text'"
}

function ConvertTo-SqlNumber([AllowNull()][object]$Value) {
    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return 'NULL'
    }
    $number = 0D
    if (-not [decimal]::TryParse([string]$Value, [Globalization.NumberStyles]::Any,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$number)) {
        return 'NULL'
    }
    return $number.ToString([Globalization.CultureInfo]::InvariantCulture)
}

function ConvertTo-CompactJson([AllowNull()][object]$Value) {
    if ($null -eq $Value) {
        return '[]'
    }
    # -InputObject keeps a single-element Object[] as a JSON array instead of
    # letting the pipeline collapse it into a scalar JSON string.
    return (ConvertTo-Json -InputObject $Value -Compress -Depth 10)
}

function ConvertTo-UrlPath([string]$Path) {
    return (($Path -split '/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
}

function ConvertTo-LocalMirrorUrl([AllowNull()][object]$Value) {
    $url = [string]$Value
    if ([string]::IsNullOrWhiteSpace($url)) {
        return ''
    }

    $localPrefix = "$($script:MirrorDomain)/admin-api/infra/file/$($script:MirrorFileConfigId)/get/"
    if ($url.StartsWith($localPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        return $url
    }

    $remoteUri = $null
    if (-not [Uri]::TryCreate($url, [UriKind]::Absolute, [ref]$remoteUri) -or
        ($remoteUri.Scheme -ne 'http' -and $remoteUri.Scheme -ne 'https')) {
        throw "Production media URL is not an absolute HTTP URL: $url"
    }

    $cacheKey = $remoteUri.GetLeftPart([UriPartial]::Path)
    if ($script:MirroredMediaUrls.ContainsKey($cacheKey)) {
        return $script:MirroredMediaUrls[$cacheKey]
    }

    $objectPath = [Uri]::UnescapeDataString($remoteUri.AbsolutePath).TrimStart('/').Replace('\', '/')
    $segments = @($objectPath -split '/' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($segments.Count -eq 0 -or $segments -contains '..') {
        throw "Production media URL contains an unsafe object path: $url"
    }
    $objectPath = $segments -join '/'

    $relativeFilePath = $objectPath.Replace('/', [IO.Path]::DirectorySeparatorChar)
    $targetFile = [IO.Path]::GetFullPath((Join-Path $script:MirrorBasePath $relativeFilePath))
    $mirrorRoot = [IO.Path]::GetFullPath($script:MirrorBasePath).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $targetFile.StartsWith($mirrorRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Production media path escaped the local mirror directory: $objectPath"
    }

    if (-not (Test-Path -LiteralPath $targetFile) -or (Get-Item -LiteralPath $targetFile).Length -eq 0) {
        $targetDirectory = Split-Path -Parent $targetFile
        [void](New-Item -ItemType Directory -Force -Path $targetDirectory)
        $temporaryFile = "$targetFile.download-$([Guid]::NewGuid().ToString('N')).tmp"
        try {
            Invoke-WebRequest -Uri $url -OutFile $temporaryFile -UseBasicParsing -TimeoutSec 60
            if (-not (Test-Path -LiteralPath $temporaryFile) -or (Get-Item -LiteralPath $temporaryFile).Length -eq 0) {
                throw "Production media download returned an empty file: $url"
            }
            Move-Item -LiteralPath $temporaryFile -Destination $targetFile -Force
        }
        finally {
            if (Test-Path -LiteralPath $temporaryFile) {
                Remove-Item -LiteralPath $temporaryFile -Force
            }
        }
    }

    $localUrl = "$localPrefix$(ConvertTo-UrlPath $objectPath)"
    $script:MirroredMediaUrls[$cacheKey] = $localUrl
    return $localUrl
}

$docker = Find-DockerExecutable
$runningContainer = & $docker ps --filter "name=^/$DockerContainer$" --format '{{.Names}}'
if ($LASTEXITCODE -ne 0 -or $runningContainer -ne $DockerContainer) {
    throw "MySQL container '$DockerContainer' is not running. Start Docker Desktop first."
}

$fileConfigQuery = @"
SELECT id, storage,
       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(config, '$.basePath')), ''),
       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(config, '$.domain')), '')
FROM infra_file_config
WHERE deleted = b'0' AND master = b'1'
LIMIT 1;
"@
$fileConfigArgs = @(
    'exec', '-e', "MYSQL_PWD=$MySqlPassword", $DockerContainer,
    'mysql', "-u$MySqlUser", '--default-character-set=utf8mb4', '-N', '-B', '-D', $Database,
    '-e', $fileConfigQuery
)
$fileConfigRow = & $docker @fileConfigArgs
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace([string]$fileConfigRow)) {
    throw 'Unable to read the local master file-storage configuration.'
}
$fileConfigParts = @(([string]($fileConfigRow | Select-Object -Last 1)) -split "`t", 4)
if ($fileConfigParts.Count -ne 4 -or [int]$fileConfigParts[1] -ne 10 -or
    [string]::IsNullOrWhiteSpace($fileConfigParts[2]) -or [string]::IsNullOrWhiteSpace($fileConfigParts[3])) {
    throw 'Production data sync requires a local master file-storage configuration (storage type 10).'
}
$script:MirrorFileConfigId = [long]$fileConfigParts[0]
$script:MirrorBasePath = [IO.Path]::GetFullPath($fileConfigParts[2])
$script:MirrorDomain = $fileConfigParts[3].TrimEnd('/')
$script:MirroredMediaUrls = @{}
[void](New-Item -ItemType Directory -Force -Path $script:MirrorBasePath)

$headers = @{
    'X-Tenant-Id' = [string]$TenantId
    'tenant-id'   = [string]$TenantId
}
$apiBase = $ProductionApiBase.TrimEnd('/')
$requestUrl = "$apiBase/campus/post/page?pageNo=1&pageSize=100&tenantId=$TenantId"
$response = Invoke-RestMethod -Uri $requestUrl -Headers $headers -Method Get -TimeoutSec 30
if ($null -eq $response -or $response.code -ne 0 -or $null -eq $response.data) {
    throw "Production API returned an invalid response: $($response.msg)"
}

$homeConfigUrl = "$apiBase/campus/home/config?tenantId=$TenantId"
$homeConfigResponse = Invoke-RestMethod -Uri $homeConfigUrl -Headers $headers -Method Get -TimeoutSec 30
if ($null -eq $homeConfigResponse -or $homeConfigResponse.code -ne 0 -or $null -eq $homeConfigResponse.data) {
    throw "Production home config API returned an invalid response: $($homeConfigResponse.msg)"
}
$homeConfig = $homeConfigResponse.data

$posts = @($response.data.list)
if ($posts.Count -eq 0) {
    throw 'Production API returned zero posts. Local synced data was left unchanged.'
}

$commentRecords = New-Object System.Collections.Generic.List[object]
foreach ($post in $posts) {
    $commentPageNo = 1
    $loadedForPost = 0
    do {
        $commentUrl = "$apiBase/campus/post/comment-page?postId=$($post.id)&pageNo=$commentPageNo&pageSize=50&sort=latest"
        $commentResponse = Invoke-RestMethod -Uri $commentUrl -Headers $headers -Method Get -TimeoutSec 30
        if ($null -eq $commentResponse -or $commentResponse.code -ne 0 -or $null -eq $commentResponse.data) {
            throw "Production comment API returned an invalid response for post $($post.id): $($commentResponse.msg)"
        }
        $pageComments = @($commentResponse.data.list)
        foreach ($comment in $pageComments) {
            $commentRecords.Add([PSCustomObject]@{ Comment = $comment; Post = $post })
        }
        $loadedForPost += $pageComments.Count
        $commentPageNo++
        $commentTotal = [int]$commentResponse.data.total
    } while ($loadedForPost -lt $commentTotal -and $pageComments.Count -gt 0)
}

$sql = New-Object System.Text.StringBuilder
[void]$sql.AppendLine('SET NAMES utf8mb4;')
[void]$sql.AppendLine("SET time_zone = '+08:00';")
[void]$sql.AppendLine('START TRANSACTION;')

$homeConfigEntries = @(
    [PSCustomObject]@{ Suffix = 'search-placeholder'; Value = [string]$homeConfig.searchPlaceholder },
    [PSCustomObject]@{ Suffix = 'notice'; Value = [string]$homeConfig.notice },
    [PSCustomObject]@{ Suffix = 'category-icon-visible'; Value = ([bool]$homeConfig.categoryIconVisible).ToString().ToLowerInvariant() },
    [PSCustomObject]@{ Suffix = 'category-title-visible'; Value = ([bool]$homeConfig.categoryTitleVisible).ToString().ToLowerInvariant() },
    [PSCustomObject]@{ Suffix = 'categories'; Value = ConvertTo-CompactJson @($homeConfig.categories) }
)
foreach ($entry in $homeConfigEntries) {
    $configKey = ConvertTo-SqlString "campus.home.$TenantId.$($entry.Suffix)"
    $configName = ConvertTo-SqlString "Production home $($entry.Suffix)"
    $configValue = ConvertTo-SqlString $entry.Value
    [void]$sql.AppendLine(@"
UPDATE infra_config SET
  category = 'campus-home-production-sync', type = 2, name = $configName, value = $configValue,
  visible = b'1', remark = 'Synced from the production public home config API',
  updater = 'production-sync', update_time = NOW(), deleted = b'0'
WHERE config_key = $configKey AND deleted = b'0';
INSERT INTO infra_config
  (category, type, name, config_key, value, visible, remark, creator, create_time, updater, update_time, deleted)
SELECT
  'campus-home-production-sync', 2, $configName, $configKey, $configValue, b'1',
  'Synced from the production public home config API', 'production-sync', NOW(), 'production-sync', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = $configKey AND deleted = b'0');
"@)
}

$authorById = @{}
foreach ($post in $posts) {
    $authorById[[string]$post.userId] = [PSCustomObject]@{
        userId = $post.userId; author = $post.author; avatar = $post.avatar
        school = $post.school; campusName = $post.campusName
    }
}
foreach ($record in $commentRecords) {
    $comment = $record.Comment
    $post = $record.Post
    $authorById[[string]$comment.userId] = [PSCustomObject]@{
        userId = $comment.userId; author = $comment.author; avatar = $comment.avatar
        school = $post.school; campusName = $post.campusName
    }
    if ($comment.replyToUserId -and -not $authorById.ContainsKey([string]$comment.replyToUserId)) {
        $authorById[[string]$comment.replyToUserId] = [PSCustomObject]@{
            userId = $comment.replyToUserId; author = $comment.replyToAuthor; avatar = ''
            school = $post.school; campusName = $post.campusName
        }
    }
    foreach ($mentionedUserId in @($comment.mentionUserIds)) {
        if ($mentionedUserId -and -not $authorById.ContainsKey([string]$mentionedUserId)) {
            $authorById[[string]$mentionedUserId] = [PSCustomObject]@{
                userId = $mentionedUserId; author = 'Production user'; avatar = ''
                school = $post.school; campusName = $post.campusName
            }
        }
    }
}

$authors = @($authorById.Values)
foreach ($author in $authors) {
    $remoteUserId = [long]$author.userId
    $syncOpenId = "prod-sync-$TenantId-$remoteUserId"
    $nickname = if ([string]::IsNullOrWhiteSpace([string]$author.author)) { 'Production user' } else { [string]$author.author }
    $avatar = if ($author.avatar) { ConvertTo-LocalMirrorUrl $author.avatar } else { '' }
    $school = if ($author.school) { [string]$author.school } else { 'Unknown school' }
    $campus = if ($author.campusName) { [string]$author.campusName } else { 'Unknown campus' }

    [void]$sql.AppendLine(@"
INSERT INTO campus_miniapp_user
  (openid, unionid, nickname, avatar, mobile, phone_country_code, school_name, campus_name, grade,
   gender, role_type, source_scene, inviter_user_id, first_login_time, last_login_time,
   creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
  ($(ConvertTo-SqlString $syncOpenId), '', $(ConvertTo-SqlString $nickname), $(ConvertTo-SqlString $avatar), '', '',
   $(ConvertTo-SqlString $school), $(ConvertTo-SqlString $campus), '', '', 'student', 'production-sync', NULL,
   NOW(), NOW(), 'production-sync', NOW(), 'production-sync', NOW(), b'0', $TenantId)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname), avatar = VALUES(avatar), school_name = VALUES(school_name),
  campus_name = VALUES(campus_name), last_login_time = NOW(), updater = 'production-sync',
  update_time = NOW(), deleted = b'0', tenant_id = VALUES(tenant_id);
"@)
}

$remoteMarkers = New-Object System.Collections.Generic.List[string]
foreach ($post in $posts) {
    $remotePostId = [long]$post.id
    $remoteUserId = [long]$post.userId
    $syncOpenId = "prod-sync-$TenantId-$remoteUserId"
    $marker = "prod-sync:$remotePostId"
    $remoteMarkers.Add($marker)

    $school = if ($post.school) { [string]$post.school } else { 'Unknown school' }
    $campus = if ($post.campusName) { [string]$post.campusName } else { 'Unknown campus' }
    $visibleRange = if ($post.visibleRange) { [string]$post.visibleRange } else { '' }
    $anonymous = "b'0'"
    $tagsJson = ConvertTo-CompactJson @($post.tags)
    $imagesJson = ConvertTo-CompactJson @($post.images | ForEach-Object { ConvertTo-LocalMirrorUrl $_ })
    $createdAt = if ($post.createTime) { "FROM_UNIXTIME($([long]$post.createTime) / 1000)" } else { 'NOW()' }

    $values = @{
        Marker        = ConvertTo-SqlString $marker
        OpenId        = ConvertTo-SqlString $syncOpenId
        Nickname      = ConvertTo-SqlString $(if ($post.author) { [string]$post.author } else { 'Production user' })
        School        = ConvertTo-SqlString $school
        Campus        = ConvertTo-SqlString $campus
        Type          = ConvertTo-SqlString $post.type
        Channel       = ConvertTo-SqlString $post.channel
        Title         = ConvertTo-SqlString $post.title
        Content       = ConvertTo-SqlString $post.content
        Price         = ConvertTo-SqlNumber $post.price
        OriginalPrice = ConvertTo-SqlNumber $post.originalPrice
        Location      = ConvertTo-SqlString $(if ($post.location) { $post.location } else { '' })
        TradeMode     = ConvertTo-SqlString $(if ($post.tradeMode) { $post.tradeMode } else { '' })
        VisibleRange  = ConvertTo-SqlString $visibleRange
        Anonymous     = $anonymous
        TagsJson      = ConvertTo-SqlString $tagsJson
        ImagesJson    = ConvertTo-SqlString $imagesJson
        Status        = [int]$post.status
        Likes         = [int]$post.likes
        Collects      = [int]$post.collects
        Comments      = [int]$post.comments
        Views         = [int]$post.views
        CreatedAt     = $createdAt
        TenantId      = $TenantId
    }

    $updateSql = @"
SET @shadow_sync_user_id := (SELECT id FROM campus_miniapp_user WHERE openid = $($values.OpenId) LIMIT 1);
SET @matched_local_user_id := (
  SELECT CASE WHEN COUNT(*) = 1 THEN MAX(id) ELSE NULL END
  FROM campus_miniapp_user
  WHERE tenant_id = $($values.TenantId)
    AND deleted = b'0'
    AND openid NOT LIKE 'prod-sync-%'
    AND nickname = $($values.Nickname)
    AND nickname <> 'Production user'
    AND school_name = $($values.School)
    AND campus_name = $($values.Campus)
);
SET @sync_user_id := COALESCE(@matched_local_user_id, @shadow_sync_user_id);
UPDATE campus_post SET
  user_id = @sync_user_id, tenant_id = $($values.TenantId), school_name = $($values.School),
  campus_name = $($values.Campus), type = $($values.Type), channel = $($values.Channel),
  title = $($values.Title), content = $($values.Content), price = $($values.Price),
  original_price = $($values.OriginalPrice), location = $($values.Location), trade_mode = $($values.TradeMode),
  visible_range = $($values.VisibleRange), contact = '', anonymous = $($values.Anonymous),
  tags_json = $($values.TagsJson), images_json = $($values.ImagesJson), status = $($values.Status),
  like_count = $($values.Likes), collect_count = $($values.Collects), comment_count = $($values.Comments),
  view_count = $($values.Views), create_time = $($values.CreatedAt),
  updater = 'production-sync', update_time = NOW(), deleted = b'0'
WHERE tenant_id = $($values.TenantId) AND creator = $($values.Marker);
INSERT INTO campus_post
  (user_id, tenant_id, school_name, campus_name, type, channel, title, content, price, original_price,
   location, trade_mode, visible_range, contact, anonymous, tags_json, images_json, status,
   like_count, collect_count, comment_count, view_count, creator, updater, create_time, update_time, deleted)
SELECT
  @sync_user_id, $($values.TenantId), $($values.School), $($values.Campus), $($values.Type),
  $($values.Channel), $($values.Title), $($values.Content), $($values.Price), $($values.OriginalPrice),
  $($values.Location), $($values.TradeMode), $($values.VisibleRange), '', $($values.Anonymous),
  $($values.TagsJson), $($values.ImagesJson), $($values.Status), $($values.Likes), $($values.Collects),
  $($values.Comments), $($values.Views), $($values.Marker), 'production-sync', $($values.CreatedAt), NOW(), b'0'
WHERE @sync_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM campus_post WHERE tenant_id = $($values.TenantId) AND creator = $($values.Marker));
"@
    [void]$sql.AppendLine($updateSql)
}

$markerList = ($remoteMarkers | ForEach-Object { ConvertTo-SqlString $_ }) -join ', '
[void]$sql.AppendLine("UPDATE campus_post SET deleted = b'1', updater = 'production-sync', update_time = NOW() WHERE tenant_id = $TenantId AND creator LIKE 'prod-sync:%' AND creator NOT IN ($markerList);")
[void]$sql.AppendLine(@"
UPDATE campus_trade_order orders
JOIN campus_post post ON post.id = orders.product_id
  AND post.tenant_id = $TenantId AND post.deleted = b'0' AND post.creator LIKE 'prod-sync:%'
SET orders.item_cover_snapshot = JSON_UNQUOTE(JSON_EXTRACT(post.images_json, '$[0]')),
    orders.updater = 'production-sync', orders.update_time = NOW()
WHERE orders.tenant_id = $TenantId AND orders.deleted = b'0'
  AND JSON_VALID(post.images_json)
  AND JSON_UNQUOTE(JSON_EXTRACT(post.images_json, '$[0]')) IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(post.images_json, '$[0]')) <> '';
"@)

$remoteCommentMarkers = New-Object System.Collections.Generic.List[string]
foreach ($record in $commentRecords) {
    $comment = $record.Comment
    $post = $record.Post
    $remoteCommentId = [long]$comment.id
    $commentMarker = "prod-sync-comment:$remoteCommentId"
    $postMarker = "prod-sync:$([long]$post.id)"
    $authorOpenId = "prod-sync-$TenantId-$([long]$comment.userId)"
    $remoteCommentMarkers.Add($commentMarker)

    $replyUserSql = 'SET @sync_reply_user_id := NULL;'
    if ($comment.replyToUserId) {
        $replyOpenId = "prod-sync-$TenantId-$([long]$comment.replyToUserId)"
        $replyUserSql = "SET @sync_reply_user_id := (SELECT id FROM campus_miniapp_user WHERE openid = $(ConvertTo-SqlString $replyOpenId) LIMIT 1);"
    }

    $mentionOpenIds = @($comment.mentionUserIds | ForEach-Object { "prod-sync-$TenantId-$([long]$_)" })
    $mentionSql = "SET @sync_mention_user_ids_json := '[]';"
    if ($mentionOpenIds.Count -gt 0) {
        $mentionOpenIdList = ($mentionOpenIds | ForEach-Object { ConvertTo-SqlString $_ }) -join ', '
        $mentionSql = "SET @sync_mention_user_ids_json := (SELECT COALESCE(CONCAT('[', GROUP_CONCAT(id ORDER BY id SEPARATOR ','), ']'), '[]') FROM campus_miniapp_user WHERE openid IN ($mentionOpenIdList));"
    }

    $commentImagesJson = ConvertTo-CompactJson @($comment.images | ForEach-Object { ConvertTo-LocalMirrorUrl $_ })
    $commentCreatedAt = if ($comment.createTime) { "FROM_UNIXTIME($([long]$comment.createTime) / 1000)" } else { 'NOW()' }
    $commentValues = @{
        Marker    = ConvertTo-SqlString $commentMarker
        Post      = ConvertTo-SqlString $postMarker
        OpenId    = ConvertTo-SqlString $authorOpenId
        Content   = ConvertTo-SqlString $comment.content
        Images    = ConvertTo-SqlString $commentImagesJson
        Status    = [int]$comment.status
        LikeCount = [int]$comment.likeCount
        CreatedAt = $commentCreatedAt
    }

    [void]$sql.AppendLine(@"
SET @sync_post_id := (SELECT id FROM campus_post WHERE tenant_id = $TenantId AND creator = $($commentValues.Post) LIMIT 1);
SET @sync_comment_user_id := (SELECT id FROM campus_miniapp_user WHERE openid = $($commentValues.OpenId) LIMIT 1);
$replyUserSql
$mentionSql
UPDATE campus_post_comment SET
  post_id = @sync_post_id, user_id = @sync_comment_user_id, parent_id = NULL,
  reply_to_user_id = @sync_reply_user_id, tenant_id = $TenantId, content = $($commentValues.Content),
  mention_user_ids_json = @sync_mention_user_ids_json, images_json = $($commentValues.Images),
  status = $($commentValues.Status), like_count = $($commentValues.LikeCount),
  create_time = $($commentValues.CreatedAt), updater = 'production-sync', update_time = NOW(), deleted = b'0'
WHERE tenant_id = $TenantId AND creator = $($commentValues.Marker);
INSERT INTO campus_post_comment
  (post_id, user_id, parent_id, reply_to_user_id, tenant_id, content, mention_user_ids_json,
   images_json, status, like_count, creator, updater, create_time, update_time, deleted)
SELECT
  @sync_post_id, @sync_comment_user_id, NULL, @sync_reply_user_id, $TenantId, $($commentValues.Content),
  @sync_mention_user_ids_json, $($commentValues.Images), $($commentValues.Status),
  $($commentValues.LikeCount), $($commentValues.Marker), 'production-sync', $($commentValues.CreatedAt), NOW(), b'0'
WHERE @sync_post_id IS NOT NULL AND @sync_comment_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM campus_post_comment WHERE tenant_id = $TenantId AND creator = $($commentValues.Marker));
"@)
}

foreach ($record in $commentRecords) {
    $comment = $record.Comment
    if (-not $comment.parentId) {
        continue
    }
    $childMarker = ConvertTo-SqlString "prod-sync-comment:$([long]$comment.id)"
    $parentMarker = ConvertTo-SqlString "prod-sync-comment:$([long]$comment.parentId)"
    [void]$sql.AppendLine(@"
UPDATE campus_post_comment child
JOIN campus_post_comment parent ON parent.tenant_id = $TenantId
  AND parent.creator = $parentMarker AND parent.deleted = b'0'
SET child.parent_id = parent.id, child.update_time = NOW()
WHERE child.tenant_id = $TenantId AND child.creator = $childMarker AND child.deleted = b'0';
"@)
}

if ($remoteCommentMarkers.Count -gt 0) {
    $commentMarkerList = ($remoteCommentMarkers | ForEach-Object { ConvertTo-SqlString $_ }) -join ', '
    [void]$sql.AppendLine("UPDATE campus_post_comment SET deleted = b'1', updater = 'production-sync', update_time = NOW() WHERE tenant_id = $TenantId AND creator LIKE 'prod-sync-comment:%' AND creator NOT IN ($commentMarkerList);")
}
else {
    [void]$sql.AppendLine("UPDATE campus_post_comment SET deleted = b'1', updater = 'production-sync', update_time = NOW() WHERE tenant_id = $TenantId AND creator LIKE 'prod-sync-comment:%';")
}

[void]$sql.AppendLine('COMMIT;')
[void]$sql.AppendLine("SELECT COUNT(*) AS synced_post_count FROM campus_post WHERE tenant_id = $TenantId AND creator LIKE 'prod-sync:%' AND deleted = b'0';")
[void]$sql.AppendLine("SELECT COUNT(*) AS synced_comment_count FROM campus_post_comment WHERE tenant_id = $TenantId AND creator LIKE 'prod-sync-comment:%' AND deleted = b'0';")

$tempName = "campus-production-sync-$([Guid]::NewGuid().ToString('N')).sql"
$localTemp = Join-Path ([IO.Path]::GetTempPath()) $tempName
$containerTemp = "/tmp/$tempName"
try {
    [IO.File]::WriteAllText($localTemp, $sql.ToString(), (New-Object Text.UTF8Encoding($false)))
    & $docker cp $localTemp "${DockerContainer}:$containerTemp"
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to copy the generated SQL into the MySQL container.'
    }
    & $docker exec -e "MYSQL_PWD=$MySqlPassword" $DockerContainer mysql "-u$MySqlUser" --default-character-set=utf8mb4 -D $Database -e "source $containerTemp"
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL rejected the production data sync transaction.'
    }
}
finally {
    if (Test-Path -LiteralPath $localTemp) {
        Remove-Item -LiteralPath $localTemp -Force
    }
}

Write-Host "Production data sync completed: $($homeConfigEntries.Count) home settings, $($posts.Count) posts, and $($commentRecords.Count) comments for tenant $TenantId." -ForegroundColor Green
