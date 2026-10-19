import { sample } from 'openapi-sampler'
import { XMLBuilder } from 'fast-xml-parser'
import { calcParamTarget } from '@/services/api/ApiDocPreviewService'
import { removeSchemaRecursion } from '@/services/api/ApiCommonService'
import { formatCurlRequest } from '@/services/api/CurlProcessService'
import { FORM_DATA, FORM_URL_ENCODED, LANG_TO_CONTENT_TYPES } from '@/consts/ApiConstants'

export const REQUEST_CODE_LANGUAGES = [
  { value: 'curl', label: 'cURL (Bash)', language: 'shell' },
  { value: 'fetch', label: 'JavaScript Fetch', language: 'javascript' },
  { value: 'axios', label: 'JavaScript Axios', language: 'javascript' },
  { value: 'python', label: 'Python Requests', language: 'python' },
  { value: 'java', label: 'Java HttpClient (JDK 11+)', language: 'java' },
  { value: 'okhttp', label: 'Java OkHttp', language: 'java' },
  { value: 'go', label: 'Go net/http', language: 'go' },
  { value: 'csharp', label: 'C# HttpClient', language: 'csharp' }
]

const quote = value => JSON.stringify(`${value ?? ''}`)
const exampleValue = schema => {
  try {
    return sample(removeSchemaRecursion(schema || {}), { skipReadOnly: true }) ?? 'value'
  } catch {
    return schema?.example ?? schema?.default ?? schema?.enum?.[0] ?? 'value'
  }
}
const firstExample = examples => {
  const entry = Object.values(examples || {})[0]
  return entry && typeof entry === 'object' ? entry.value : entry
}

// 文档模式只使用接口定义，不写入调试缓存。
export const buildDocCodeTarget = (project, doc) => {
  const target = calcParamTarget(project || {}, doc)
  target.targetUrl = doc.targetUrl || 'https://your-server.example'
  const parameters = JSON.parse(doc.parametersSchema?.schemaContent || '[]')
  for (const [key, location] of [['pathParams', 'path'], ['requestParams', 'query'], ['headerParams', 'header']]) {
    target[key].forEach(item => {
      const param = parameters.find(param => param.in === location && param.name === item.name)
      const value = param?.example ?? firstExample(param?.examples) ?? exampleValue(item.schema)
      item.value = Array.isArray(value)
        ? location === 'query' && param?.explode !== false ? value : value.join(',')
        : typeof value === 'object' ? JSON.stringify(value) : value
    })
  }
  const security = JSON.parse((doc.securityRequirements || project?.securityRequirements)?.schemaContent || '[]')
  const schemes = JSON.parse(project?.securitySchemas?.schemaContent || '{}')
  // OpenAPI security 数组为 OR，单个对象中的认证方案为 AND。
  Object.keys(security[0] || {}).forEach(key => {
    const scheme = schemes[key]
    if (!scheme) return
    const apiKey = scheme.type === 'apiKey'
    const name = apiKey ? scheme.name : 'Authorization'
    let value = apiKey ? 'YOUR_API_KEY' : scheme.scheme === 'basic' ? 'Basic BASE64_USERNAME_PASSWORD' : 'Bearer YOUR_TOKEN'
    const params = apiKey && scheme.in === 'query' ? target.requestParams : target.headerParams
    if (apiKey && scheme.in === 'cookie') value = `${name}=YOUR_API_KEY`
    const headerName = apiKey && scheme.in === 'cookie' ? 'Cookie' : name
    const existing = params.find(item => item.name.toLowerCase() === headerName.toLowerCase())
    if (existing && apiKey && scheme.in === 'cookie') {
      existing.value = `${existing.value}; ${value}`
    } else if (!existing) {
      params.push({ name: headerName, value, enabled: true })
    }
  })
  const schema = target.requestBodySchema?.find(schema => schema.__contentType === target.requestContentType) || target.requestBodySchema?.[0]
  if (schema) {
    target.requestContentType = schema.__contentType || target.requestContentType
    const rawExample = doc.requestsSchemas?.find(item => item.contentType === target.requestContentType)?.examples
    const examples = typeof rawExample === 'string' ? JSON.parse(rawExample) : rawExample
    const explicitExample = firstExample(examples)
    const value = explicitExample ?? exampleValue(schema)
    const formType = [FORM_DATA, FORM_URL_ENCODED].find(type => LANG_TO_CONTENT_TYPES[type] === target.requestContentType)
    if (formType) {
      target[formType] = Object.entries(value || {}).map(([name, value]) => ({
        name,
        value: formType === FORM_DATA && schema.properties?.[name]?.format === 'binary'
          ? [{ name: 'file.bin', raw: new Blob([]) }]
          : typeof value === 'object' ? JSON.stringify(value) : value,
        enabled: true
      }))
    } else {
      const json = target.requestContentType?.includes('json')
      let serializedExample = false
      if (json && typeof explicitExample === 'string') {
        try {
          JSON.parse(explicitExample)
          serializedExample = true
        } catch { /* A JSON string value still needs quoting. */ }
      }
      target.requestBody = json
        ? serializedExample ? explicitExample : JSON.stringify(value, null, 2)
        : typeof value === 'string'
          ? value
          : target.requestContentType?.includes('xml')
            ? new XMLBuilder({ ignoreAttributes: false, format: true }).build({ [schema.xml?.name || 'root']: value })
            : JSON.stringify(value, null, 2)
    }
  }
  return target
}

const isMultipart = request => request.hasBody && request.contentType === LANG_TO_CONTENT_TYPES[FORM_DATA]
const rawBody = request => request.contentType === LANG_TO_CONTENT_TYPES[FORM_URL_ENCODED]
  ? new URLSearchParams(request.fields.map(field => [field.name, field.value])).toString()
  : request.body

const codeHeaders = (request, excluded = []) => Object.entries(request.headers).filter(([name]) => {
  const key = name.toLowerCase()
  return key !== 'content-length' && !excluded.includes(key) && !(isMultipart(request) && key === 'content-type')
})

const javascriptCode = (request, axios) => {
  const lines = axios ? ['import axios from "axios";', ''] : []
  const multipart = isMultipart(request)
  if (multipart) {
    if (request.fields.some(field => field.file)) {
      lines.push('// Select files using <input type="file" multiple>.', 'const files = document.querySelector("input[type=file]").files;')
    }
    lines.push('const body = new FormData();')
    let index = 0
    request.fields.forEach(field => lines.push(`body.append(${quote(field.name)}, ${field.file ? `files[${index++}]` : quote(field.value)});`))
  } else if (request.hasBody) {
    lines.push(`const body = ${quote(rawBody(request))};`)
  }
  const config = [`  method: ${quote(request.method)}`, `  headers: ${JSON.stringify(request.headers, null, 2).replace(/\n/g, '\n  ')}`]
  if (request.hasBody) config.push(axios ? '  data: body' : '  body')
  if (axios) {
    config.unshift(`  url: ${quote(request.url)}`)
    lines.push(`const response = await axios({\n${config.join(',\n')}\n});`, 'console.log(response.data);')
  } else {
    lines.push(`const response = await fetch(${quote(request.url)}, {\n${config.join(',\n')}\n});`, 'console.log(await response.text());')
  }
  return lines.join('\n')
}

const pythonCode = request => {
  const multipart = isMultipart(request)
  const lines = ['import requests', ...(multipart ? ['from contextlib import ExitStack'] : []), '', `url = ${quote(request.url)}`, 'headers = {',
    ...Object.entries(request.headers).map(([name, value]) => `    ${quote(name)}: ${quote(value)},`), '}']
  if (multipart) {
    lines.push('', 'with ExitStack() as stack:', '    files = [')
    request.fields.forEach(field => {
      const value = field.file ? `(${quote(field.value)}, stack.enter_context(open(${quote(field.value)}, "rb")))` : `(None, ${quote(field.value)})`
      lines.push(`        (${quote(field.name)}, ${value}),`)
    })
    lines.push('    ]', `    response = requests.request(${quote(request.method)}, url, headers=headers, files=files, timeout=30)`)
  } else {
    if (request.hasBody) lines.push(`body = ${quote(rawBody(request))}.encode("utf-8")`)
    lines.push(`response = requests.request(${quote(request.method)}, url, headers=headers${request.hasBody ? ', data=body' : ''}, timeout=30)`)
  }
  lines.push('print(response.status_code)', 'print(response.text)')
  return lines.join('\n')
}

const javaCode = request => {
  const multipart = isMultipart(request)
  const lines = ['import java.net.URI;', 'import java.net.http.HttpClient;', 'import java.net.http.HttpRequest;', 'import java.net.http.HttpResponse;', 'import java.nio.charset.StandardCharsets;',
    ...(multipart ? ['import java.nio.file.Files;', 'import java.nio.file.Path;', 'import java.io.ByteArrayOutputStream;', 'import java.util.UUID;'] : []),
    '', 'public class ApiRequestExample {', '    public static void main(String[] args) throws Exception {']
  let publisher = 'HttpRequest.BodyPublishers.noBody()'
  if (multipart) {
    lines.push('        String boundary = "----JavaBoundary" + UUID.randomUUID();', '        ByteArrayOutputStream body = new ByteArrayOutputStream();')
    request.fields.forEach(field => {
      const name = field.name.replace(/[\r\n"]/g, '_')
      const filename = field.value.replace(/[\r\n"\\/]/g, '_')
      const disposition = `\r\nContent-Disposition: form-data; name="${name}"${field.file ? `; filename="${filename}"\r\nContent-Type: application/octet-stream` : ''}\r\n\r\n`
      lines.push(`        body.write(("--" + boundary + ${quote(disposition)}).getBytes(StandardCharsets.UTF_8));`,
        `        body.write(${field.file ? `Files.readAllBytes(Path.of(${quote(field.value)}))` : `${quote(field.value)}.getBytes(StandardCharsets.UTF_8)`});`,
        '        body.write("\\r\\n".getBytes(StandardCharsets.UTF_8));')
    })
    lines.push('        body.write(("--" + boundary + "--\\r\\n").getBytes(StandardCharsets.UTF_8));')
    publisher = 'HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())'
  } else if (request.hasBody) {
    lines.push(`        String body = ${quote(rawBody(request))};`)
    publisher = 'HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)'
  }
  lines.push(`        HttpRequest request = HttpRequest.newBuilder(URI.create(${quote(request.url)}))`)
  // HttpClient manages these transport headers itself.
  codeHeaders(request, ['connection', 'expect', 'host', 'upgrade'])
    .forEach(([name, value]) => lines.push(`            .header(${quote(name)}, ${quote(value)})`))
  if (multipart) lines.push('            .header("Content-Type", "multipart/form-data; boundary=" + boundary)')
  lines.push(`            .method(${quote(request.method)}, ${publisher})`, '            .build();',
    '        HttpResponse<String> response = HttpClient.newHttpClient()', '            .send(request, HttpResponse.BodyHandlers.ofString());',
    '        System.out.println(response.statusCode());', '        System.out.println(response.body());', '    }', '}')
  return lines.join('\n')
}

const okhttpCode = request => {
  const multipart = isMultipart(request)
  const lines = ['// Dependency: com.squareup.okhttp3:okhttp', 'import okhttp3.*;',
    ...(multipart && request.fields.some(field => field.file) ? ['import java.io.File;'] : []),
    '', 'public class ApiRequestExample {', '    public static void main(String[] args) throws Exception {',
    '        OkHttpClient client = new OkHttpClient();']
  if (multipart) {
    lines.push('        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)')
    request.fields.forEach(field => lines.push(field.file
      ? `            .addFormDataPart(${quote(field.name)}, new File(${quote(field.value)}).getName(), RequestBody.create(MediaType.parse("application/octet-stream"), new File(${quote(field.value)})))`
      : `            .addFormDataPart(${quote(field.name)}, ${quote(field.value)})`))
    lines.push('            .build();')
  } else if (request.hasBody || ['POST', 'PUT', 'PATCH', 'PROPPATCH', 'REPORT'].includes(request.method)) {
    lines.push(`        RequestBody body = RequestBody.create(${request.contentType ? `MediaType.parse(${quote(request.contentType)})` : 'null'}, ${quote(request.hasBody ? rawBody(request) : '')});`)
  } else {
    lines.push('        RequestBody body = null;')
  }
  lines.push(`        Request request = new Request.Builder().url(${quote(request.url)})`)
  codeHeaders(request)
    .forEach(([name, value]) => lines.push(`            .header(${quote(name)}, ${quote(value)})`))
  lines.push(`            .method(${quote(request.method)}, body)`, '            .build();',
    '        try (Response response = client.newCall(request).execute()) {',
    '            System.out.println(response.code());',
    '            System.out.println(response.body() != null ? response.body().string() : "");', '        }', '    }', '}')
  return lines.join('\n')
}

const goCode = request => {
  const multipart = isMultipart(request)
  const lines = ['package main', '', 'import (', '    "fmt"', '    "io"', '    "net/http"', '    "time"',
    ...(multipart ? ['    "bytes"', '    "mime/multipart"'] : request.hasBody ? ['    "strings"'] : []),
    ...(multipart && request.fields.some(field => field.file) ? ['    "os"', '    "path/filepath"'] : []),
    ')', '', 'func check(err error) {', '    if err != nil { panic(err) }', '}', '', 'func main() {']
  if (multipart) {
    lines.push('    body := &bytes.Buffer{}', '    writer := multipart.NewWriter(body)')
    request.fields.forEach(field => {
      if (field.file) {
        lines.push('    {', `        data, err := os.ReadFile(${quote(field.value)})`, '        check(err)',
          `        part, err := writer.CreateFormFile(${quote(field.name)}, filepath.Base(${quote(field.value)}))`,
          '        check(err)', '        _, err = part.Write(data)', '        check(err)', '    }')
      } else lines.push(`    check(writer.WriteField(${quote(field.name)}, ${quote(field.value)}))`)
    })
    lines.push('    check(writer.Close())')
  } else if (request.hasBody) lines.push(`    body := strings.NewReader(${quote(rawBody(request))})`)
  lines.push(`    req, err := http.NewRequest(${quote(request.method)}, ${quote(request.url)}, ${request.hasBody ? 'body' : 'nil'})`, '    check(err)')
  codeHeaders(request)
    .forEach(([name, value]) => lines.push(name.toLowerCase() === 'host'
      ? `    req.Host = ${quote(value)}`
      : `    req.Header.Set(${quote(name)}, ${quote(value)})`))
  if (multipart) lines.push('    req.Header.Set("Content-Type", writer.FormDataContentType())')
  lines.push('    client := &http.Client{Timeout: 30 * time.Second}', '    resp, err := client.Do(req)', '    check(err)', '    defer resp.Body.Close()',
    '    data, err := io.ReadAll(resp.Body)', '    check(err)', '    fmt.Println(resp.StatusCode)', '    fmt.Println(string(data))', '}')
  return lines.join('\n')
}

const csharpCode = request => {
  const multipart = isMultipart(request)
  const lines = ['using System;', 'using System.Net.Http;', 'using System.Text;', 'using System.Threading.Tasks;',
    ...(multipart && request.fields.some(field => field.file) ? ['using System.IO;'] : []),
    '', 'class ApiRequestExample', '{', '    static async Task Main()', '    {',
    '        using var client = new HttpClient();',
    `        using var request = new HttpRequestMessage(new HttpMethod(${quote(request.method)}), ${quote(request.url)});`]
  if (multipart) {
    lines.push('        var body = new MultipartFormDataContent();', '        request.Content = body;')
    request.fields.forEach(field => lines.push(field.file
      ? `        body.Add(new StreamContent(File.OpenRead(${quote(field.value)})), ${quote(field.name)}, Path.GetFileName(${quote(field.value)}));`
      : `        body.Add(new StringContent(${quote(field.value)}, Encoding.UTF8), ${quote(field.name)});`))
  } else if (request.hasBody) {
    lines.push(`        request.Content = new ByteArrayContent(Encoding.UTF8.GetBytes(${quote(rawBody(request))}));`)
  }
  codeHeaders(request)
    .forEach(([name, value]) => {
      lines.push(`        if (!request.Headers.TryAddWithoutValidation(${quote(name)}, ${quote(value)}))`, '        {',
        '            request.Content ??= new ByteArrayContent(Array.Empty<byte>());',
        `            request.Content.Headers.Remove(${quote(name)});`,
        `            request.Content.Headers.TryAddWithoutValidation(${quote(name)}, ${quote(value)});`, '        }')
    })
  lines.push('        using var response = await client.SendAsync(request);', '        Console.WriteLine((int)response.StatusCode);',
    '        Console.WriteLine(await response.Content.ReadAsStringAsync());', '    }', '}')
  return lines.join('\n')
}

export const generateRequestCode = (request, language) => {
  if (!request) return ''
  switch (language) {
    case 'fetch': return javascriptCode(request, false)
    case 'axios': return javascriptCode(request, true)
    case 'python': return pythonCode(request)
    case 'java': return javaCode(request)
    case 'okhttp': return okhttpCode(request)
    case 'go': return goCode(request)
    case 'csharp': return csharpCode(request)
    default: return formatCurlRequest(request)
  }
}
