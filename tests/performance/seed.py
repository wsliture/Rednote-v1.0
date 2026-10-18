#!/usr/bin/env python3
"""Seed exactly 10k additional notes with shared real image, 20k likes and 30k comments.
Only isolated validation databases are accepted; the production database is rejected.
"""
import argparse,json,re,subprocess,os,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--database',default='rednote_browser');args=p.parse_args()
if not re.fullmatch(r'rednote_(browser|test[a-zA-Z0-9_]*)',args.database):raise SystemExit('Only isolated browser/test databases are allowed')
f=json.loads(Path('target/browser-fixture.json').read_text());user=f['username'];fid=f['asset']
if not re.fullmatch('[a-zA-Z0-9_]+',user) or not fid.isdecimal():raise SystemExit('Invalid fixture')
sql=f"""
SET SESSION cte_max_recursion_depth=11000;
SET @author=(SELECT id FROM app_user WHERE username_normalized='{user}');
SET @other=(SELECT id FROM app_user WHERE role='ADMIN' LIMIT 1);
SET @image={fid}; SET @upper=(SELECT COALESCE(MAX(id),0) FROM note);
START TRANSACTION;
INSERT INTO note(author_id,title,body,published_at,updated_at,version)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n<10000)
SELECT @author,CONCAT('benchmark ',n),'representative text for search and feed',TIMESTAMPADD(SECOND,-n,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6),0 FROM seq;
INSERT INTO note_image(note_id,file_id,sort_order) SELECT id,@image,1 FROM note WHERE id>@upper;
INSERT INTO note_like(user_id,note_id,created_at) SELECT @author,id,UTC_TIMESTAMP(6) FROM note WHERE id>@upper;
INSERT INTO note_like(user_id,note_id,created_at) SELECT @other,id,UTC_TIMESTAMP(6) FROM note WHERE id>@upper;
INSERT INTO `comment`(note_id,author_id,content,created_at) SELECT id,@author,'benchmark root',UTC_TIMESTAMP(6) FROM note WHERE id>@upper;
INSERT INTO `comment`(note_id,author_id,root_comment_id,target_comment_id,reply_to_user_id,content,created_at)
SELECT note_id,@other,id,id,@author,'benchmark reply',UTC_TIMESTAMP(6) FROM `comment` WHERE note_id>@upper AND root_comment_id IS NULL;
INSERT INTO `comment`(note_id,author_id,root_comment_id,target_comment_id,reply_to_user_id,content,created_at)
SELECT note_id,@author,id,id,@author,'benchmark reply two',UTC_TIMESTAMP(6) FROM `comment` WHERE note_id>@upper AND root_comment_id IS NULL;
UPDATE file_asset SET unreferenced_since=NULL WHERE id=@image;
COMMIT;
SELECT 'seeded_notes',COUNT(*) FROM note WHERE id>@upper;
EXPLAIN ANALYZE SELECT id FROM note WHERE deleted_at IS NULL ORDER BY published_at DESC,id DESC LIMIT 21;
EXPLAIN ANALYZE SELECT id FROM note WHERE deleted_at IS NULL AND (title LIKE '%benchmark%' OR body LIKE '%benchmark%') ORDER BY published_at DESC,id DESC LIMIT 21;
"""
mysql=os.getenv('MYSQL_BIN') or shutil.which('mysql') or '/usr/local/mysql/bin/mysql'
password=os.getenv('REDNOTE_TEST_DB_PASSWORD') or os.getenv('REDNOTE_DB_PASSWORD')
if password is None:
    local=Path('.env.dev.properties')
    for line in local.read_text().splitlines() if local.exists() else []:
        if line.startswith('rednote.dev.db.password='):password=line.split('=',1)[1];break
if password is None:raise SystemExit('Set REDNOTE_TEST_DB_PASSWORD or local password configuration')
env=os.environ.copy();env['MYSQL_PWD']=password
result=subprocess.run([mysql,'--protocol=TCP','-h',os.getenv('REDNOTE_TEST_DB_HOST','127.0.0.1'),'-P',os.getenv('REDNOTE_TEST_DB_PORT','3306'),'-u',os.getenv('REDNOTE_TEST_DB_USER','root'),args.database],input=sql,text=True,capture_output=True,check=True,env=env)
Path('target/performance-seed.txt').write_text(result.stdout);print(result.stdout)
