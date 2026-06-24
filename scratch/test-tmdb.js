import fs from 'fs';

function test() {
  const filePath = 'C:\\Users\\troyh\\.gemini\\antigravity\\brain\\a01f2c55-f1c9-43ec-aa96-72ed815df72d\\.system_generated\\steps\\839\\content.md';
  const content = fs.readFileSync(filePath, 'utf8');
  
  console.log('Searching for hf.space in saved HTML...');
  const lines = content.split('\n');
  lines.forEach((line, index) => {
    if (line.includes('hf.space') || line.includes('missourimonster') || line.includes('stream') || line.includes('vyla.pages.dev')) {
      console.log(`Line ${index + 1}: ${line.trim().substring(0, 200)}`);
    }
  });
}

test();
