import type * as Monaco from 'monaco-editor'

import type { CodeLanguageCode } from '@/api/code'

/** The Monaco language id for each language codeexec-service can run. */
export const MONACO_LANGUAGE: Record<CodeLanguageCode, string> = {
  JAVA: 'java',
  PYTHON: 'python',
  C: 'c',
  CPP: 'cpp',
  CSHARP: 'csharp',
  SQL: 'sql',
}

/** [label, snippet, detail]. Snippet syntax is Monaco's: $1 tab stops, ${1:name} placeholders, $0 the final cursor. */
type Snippet = readonly [string, string, string]

const KEYWORDS: Record<string, string[]> = {
  java: [
    'abstract', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'continue', 'default', 'do', 'double',
    'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'if', 'implements', 'import', 'instanceof', 'int',
    'interface', 'long', 'new', 'null', 'package', 'private', 'protected', 'public', 'return', 'short', 'static',
    'super', 'switch', 'this', 'throw', 'throws', 'true', 'false', 'try', 'void', 'while',
  ],
  python: [
    'and', 'as', 'break', 'class', 'continue', 'def', 'elif', 'else', 'except', 'False', 'finally', 'for', 'from', 'if',
    'import', 'in', 'is', 'lambda', 'None', 'not', 'or', 'pass', 'raise', 'return', 'True', 'try', 'while', 'with',
    'yield', 'print', 'input', 'len', 'range', 'int', 'str', 'float', 'list', 'dict', 'set', 'tuple', 'sorted', 'sum',
    'min', 'max', 'abs', 'enumerate', 'zip', 'map', 'filter',
  ],
  c: [
    'auto', 'break', 'case', 'char', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum', 'extern', 'float',
    'for', 'goto', 'if', 'int', 'long', 'return', 'short', 'signed', 'sizeof', 'static', 'struct', 'switch', 'typedef',
    'union', 'unsigned', 'void', 'while', 'printf', 'scanf', 'malloc', 'free', 'strlen', 'strcpy', 'strcmp',
  ],
  cpp: [
    'auto', 'bool', 'break', 'case', 'catch', 'char', 'class', 'const', 'continue', 'default', 'delete', 'do', 'double',
    'else', 'enum', 'false', 'float', 'for', 'if', 'int', 'long', 'namespace', 'new', 'nullptr', 'private', 'public',
    'return', 'short', 'static', 'struct', 'switch', 'template', 'this', 'throw', 'true', 'try', 'typename', 'using',
    'virtual', 'void', 'while', 'cout', 'cin', 'endl', 'string', 'vector', 'map', 'set', 'sort',
  ],
  csharp: [
    'abstract', 'bool', 'break', 'case', 'catch', 'char', 'class', 'const', 'continue', 'default', 'do', 'double',
    'else', 'enum', 'false', 'finally', 'float', 'for', 'foreach', 'if', 'in', 'int', 'interface', 'internal', 'long',
    'namespace', 'new', 'null', 'override', 'private', 'protected', 'public', 'return', 'static', 'string', 'struct',
    'switch', 'this', 'throw', 'true', 'try', 'using', 'var', 'virtual', 'void', 'while', 'Console', 'List',
  ],
  sql: [
    'SELECT', 'FROM', 'WHERE', 'GROUP BY', 'ORDER BY', 'HAVING', 'INSERT INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE FROM',
    'CREATE TABLE', 'DROP TABLE', 'ALTER TABLE', 'JOIN', 'INNER JOIN', 'LEFT JOIN', 'ON', 'AS', 'DISTINCT', 'LIMIT',
    'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'AND', 'OR', 'NOT', 'NULL', 'IN', 'LIKE', 'BETWEEN', 'PRIMARY KEY', 'INTEGER',
    'TEXT', 'REAL',
  ],
}

const SNIPPETS: Record<string, Snippet[]> = {
  java: [
    ['main', 'public static void main(String[] args) {\n\t$0\n}', 'main method'],
    ['psvm', 'public static void main(String[] args) {\n\t$0\n}', 'main method'],
    ['sout', 'System.out.println($0);', 'print a line'],
    ['soutv', 'System.out.println("${1:x} = " + ${1:x});', 'print a variable'],
    ['sysout', 'System.out.println($0);', 'print a line'],
    ['fori', 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t$0\n}', 'for loop'],
    ['foreach', 'for (${1:String} ${2:item} : ${3:items}) {\n\t$0\n}', 'enhanced for loop'],
    ['while', 'while (${1:condition}) {\n\t$0\n}', 'while loop'],
    ['ifelse', 'if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}', 'if / else'],
    ['trycatch', 'try {\n\t$1\n} catch (${2:Exception} e) {\n\t$0\n}', 'try / catch'],
    ['class', 'public class ${1:Main} {\n\t$0\n}', 'class'],
    ['scanner', 'Scanner ${1:sc} = new Scanner(System.in);', 'read from standard input'],
    ['list', 'List<${1:Integer}> ${2:list} = new ArrayList<>();', 'ArrayList'],
    ['map', 'Map<${1:String}, ${2:Integer}> ${3:map} = new HashMap<>();', 'HashMap'],
    ['switch', 'switch (${1:value}) {\n\tcase ${2:1}:\n\t\t$0\n\t\tbreak;\n\tdefault:\n\t\tbreak;\n}', 'switch'],
  ],
  python: [
    ['def', 'def ${1:name}(${2:args}):\n\t${0:pass}', 'function'],
    ['main', 'if __name__ == "__main__":\n\t${0:main()}', 'entry point'],
    ['for', 'for ${1:i} in range(${2:n}):\n\t$0', 'for loop'],
    ['forin', 'for ${1:item} in ${2:items}:\n\t$0', 'for each'],
    ['while', 'while ${1:condition}:\n\t$0', 'while loop'],
    ['ifelse', 'if ${1:condition}:\n\t$2\nelse:\n\t$0', 'if / else'],
    ['try', 'try:\n\t$1\nexcept ${2:Exception} as e:\n\t$0', 'try / except'],
    ['class', 'class ${1:Name}:\n\tdef __init__(self${2:, args}):\n\t\t$0', 'class'],
    ['inp', '${1:x} = int(input())', 'read an int'],
    ['listcomp', '[${1:x} for ${1:x} in ${2:items}]', 'list comprehension'],
  ],
  c: [
    ['main', 'int main(void) {\n\t$0\n\treturn 0;\n}', 'main function'],
    ['inc', '#include <${1:stdio.h}>', 'include'],
    ['printf', 'printf("${1:%d}\\n", ${2:x});', 'print'],
    ['scanf', 'scanf("${1:%d}", &${2:x});', 'read'],
    ['for', 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t$0\n}', 'for loop'],
    ['while', 'while (${1:condition}) {\n\t$0\n}', 'while loop'],
    ['ifelse', 'if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}', 'if / else'],
    ['struct', 'struct ${1:Name} {\n\t$0\n};', 'struct'],
  ],
  cpp: [
    ['main', 'int main() {\n\t$0\n\treturn 0;\n}', 'main function'],
    ['inc', '#include <${1:iostream}>', 'include'],
    ['cout', 'std::cout << ${1:x} << std::endl;', 'print'],
    ['cin', 'std::cin >> ${1:x};', 'read'],
    ['for', 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t$0\n}', 'for loop'],
    ['forr', 'for (auto& ${1:item} : ${2:items}) {\n\t$0\n}', 'range for'],
    ['while', 'while (${1:condition}) {\n\t$0\n}', 'while loop'],
    ['ifelse', 'if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}', 'if / else'],
    ['vec', 'std::vector<${1:int}> ${2:v};', 'vector'],
    ['class', 'class ${1:Name} {\npublic:\n\t$0\n};', 'class'],
  ],
  csharp: [
    ['main', 'static void Main(string[] args)\n{\n\t$0\n}', 'Main method'],
    ['cw', 'Console.WriteLine($0);', 'print a line'],
    ['cr', 'Console.ReadLine()', 'read a line'],
    ['for', 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++)\n{\n\t$0\n}', 'for loop'],
    ['foreach', 'foreach (var ${1:item} in ${2:items})\n{\n\t$0\n}', 'foreach'],
    ['while', 'while (${1:condition})\n{\n\t$0\n}', 'while loop'],
    ['ifelse', 'if (${1:condition})\n{\n\t$2\n}\nelse\n{\n\t$0\n}', 'if / else'],
    ['class', 'class ${1:Name}\n{\n\t$0\n}', 'class'],
    ['list', 'var ${1:list} = new List<${2:int}>();', 'List'],
  ],
  sql: [
    ['sel', 'SELECT ${1:*}\nFROM ${2:table}\nWHERE ${3:condition};', 'select'],
    ['ins', 'INSERT INTO ${1:table} (${2:columns})\nVALUES (${3:values});', 'insert'],
    ['upd', 'UPDATE ${1:table}\nSET ${2:column} = ${3:value}\nWHERE ${4:condition};', 'update'],
    ['del', 'DELETE FROM ${1:table}\nWHERE ${2:condition};', 'delete'],
    ['create', 'CREATE TABLE ${1:name} (\n\tid INTEGER PRIMARY KEY,\n\t${2:column} TEXT\n);', 'create table'],
    ['join', 'SELECT ${1:*}\nFROM ${2:a}\nJOIN ${3:b} ON ${2:a}.${4:id} = ${3:b}.${5:a_id};', 'join'],
    ['group', 'SELECT ${1:column}, COUNT(*)\nFROM ${2:table}\nGROUP BY ${1:column};', 'group by'],
  ],
}

/** What follows a dot, keyed by the word before it. `*` is offered when the word is a variable we cannot type. */
const JAVA_MEMBERS: Record<string, Snippet[]> = {
  'System.out': [
    ['println', 'println($0)', 'print a line'],
    ['print', 'print($0)', 'print'],
    ['printf', 'printf("${1:%d}%n", $0)', 'formatted print'],
  ],
  System: [
    ['out', 'out', 'standard output'],
    ['in', 'in', 'standard input'],
    ['currentTimeMillis', 'currentTimeMillis()', 'time in ms'],
    ['nanoTime', 'nanoTime()', 'nanoseconds'],
    ['exit', 'exit(${1:0})', 'stop the program'],
  ],
  Math: [
    ['max', 'max(${1:a}, ${2:b})', 'larger of two'],
    ['min', 'min(${1:a}, ${2:b})', 'smaller of two'],
    ['abs', 'abs(${1:x})', 'absolute value'],
    ['pow', 'pow(${1:base}, ${2:exp})', 'power'],
    ['sqrt', 'sqrt(${1:x})', 'square root'],
    ['floor', 'floor(${1:x})', 'round down'],
    ['ceil', 'ceil(${1:x})', 'round up'],
    ['round', 'round(${1:x})', 'round'],
    ['random', 'random()', 'random 0..1'],
  ],
  Integer: [
    ['parseInt', 'parseInt(${1:s})', 'String to int'],
    ['valueOf', 'valueOf(${1:x})', 'to Integer'],
    ['toString', 'toString(${1:n})', 'int to String'],
    ['MAX_VALUE', 'MAX_VALUE', 'largest int'],
    ['MIN_VALUE', 'MIN_VALUE', 'smallest int'],
    ['compare', 'compare(${1:a}, ${2:b})', 'compare two ints'],
  ],
  String: [
    ['valueOf', 'valueOf(${1:x})', 'to String'],
    ['format', 'format("${1:%d}", ${2:x})', 'formatted String'],
    ['join', 'join("${1:,}", ${2:items})', 'join with a separator'],
  ],
  Arrays: [
    ['sort', 'sort(${1:array})', 'sort in place'],
    ['toString', 'toString(${1:array})', 'array to text'],
    ['asList', 'asList(${1:items})', 'array to List'],
    ['fill', 'fill(${1:array}, ${2:value})', 'fill'],
    ['copyOf', 'copyOf(${1:array}, ${2:length})', 'copy'],
    ['equals', 'equals(${1:a}, ${2:b})', 'compare arrays'],
  ],
  Collections: [
    ['sort', 'sort(${1:list})', 'sort a list'],
    ['reverse', 'reverse(${1:list})', 'reverse a list'],
    ['max', 'max(${1:collection})', 'largest'],
    ['min', 'min(${1:collection})', 'smallest'],
    ['swap', 'swap(${1:list}, ${2:i}, ${3:j})', 'swap two items'],
  ],
  '*': [
    ['length', 'length()', 'String length'],
    ['charAt', 'charAt(${1:i})', 'character at index'],
    ['substring', 'substring(${1:from}, ${2:to})', 'part of a String'],
    ['equals', 'equals(${1:other})', 'compare'],
    ['equalsIgnoreCase', 'equalsIgnoreCase(${1:other})', 'compare, ignoring case'],
    ['toUpperCase', 'toUpperCase()', 'upper case'],
    ['toLowerCase', 'toLowerCase()', 'lower case'],
    ['trim', 'trim()', 'strip spaces'],
    ['split', 'split("${1: }")', 'split into an array'],
    ['indexOf', 'indexOf(${1:x})', 'first position'],
    ['contains', 'contains(${1:x})', 'contains'],
    ['toCharArray', 'toCharArray()', 'String to char[]'],
    ['isEmpty', 'isEmpty()', 'is empty'],
    ['size', 'size()', 'List / Map size'],
    ['add', 'add(${1:item})', 'add to a List'],
    ['get', 'get(${1:index})', 'get from a List'],
    ['remove', 'remove(${1:x})', 'remove'],
    ['put', 'put(${1:key}, ${2:value})', 'add to a Map'],
    ['containsKey', 'containsKey(${1:key})', 'Map has key'],
    ['getOrDefault', 'getOrDefault(${1:key}, ${2:fallback})', 'Map get with fallback'],
    ['append', 'append(${1:x})', 'StringBuilder append'],
    ['reverse', 'reverse()', 'StringBuilder reverse'],
    ['toString', 'toString()', 'to String'],
    ['nextInt', 'nextInt()', 'Scanner: read an int'],
    ['nextLine', 'nextLine()', 'Scanner: read a line'],
    ['next', 'next()', 'Scanner: read a word'],
  ],
}

let registered = false

/** Registers suggestions for every language once. Monaco's own word-based suggestions keep working alongside these. */
export function registerCompletions(monaco: typeof Monaco) {
  if (registered) return
  registered = true

  for (const [language, snippets] of Object.entries(SNIPPETS)) {
    monaco.languages.registerCompletionItemProvider(language, {
      provideCompletionItems(model, position) {
        const word = model.getWordUntilPosition(position)
        const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn)
        const snippetItems = snippets.map(([label, text, detail]) => ({
          label,
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: text,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          detail,
          range,
        }))
        const keywordItems = (KEYWORDS[language] ?? []).map((label) => ({
          label,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: label,
          range,
        }))
        return { suggestions: [...snippetItems, ...keywordItems] }
      },
    })
  }

  monaco.languages.registerCompletionItemProvider('java', {
    triggerCharacters: ['.'],
    provideCompletionItems(model, position) {
      const before = model.getValueInRange(new monaco.Range(position.lineNumber, 1, position.lineNumber, position.column))
      const match = /([A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\.(\w*)$/.exec(before)
      if (!match) return { suggestions: [] }

      const receiver = match[1]
      const members = JAVA_MEMBERS[receiver] ?? JAVA_MEMBERS['*']
      const start = position.column - match[2].length
      const range = new monaco.Range(position.lineNumber, start, position.lineNumber, position.column)
      return {
        suggestions: members.map(([label, text, detail]) => ({
          label,
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: text,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          detail,
          range,
        })),
      }
    },
  })
}
