# -*- coding: utf-8 -*-
# read unihan.txt and save it as a db

import psyco; psyco.full()

from sqlalchemy import (Table, Integer, Float, Unicode, Column, MetaData,
                        ForeignKey, Boolean, String, Date, UniqueConstraint,
                        UnicodeText)
from sqlalchemy import (create_engine)
from sqlalchemy.orm import mapper, sessionmaker, relation, backref, \
     object_session as _object_session
from sqlalchemy.sql import select, text, and_
from sqlalchemy.exceptions import DBAPIError
import re

metadata = MetaData()

unihanTable = Table(
    'unihan', metadata,
    Column("id", Integer, primary_key=True),
    Column("mandarin", UnicodeText),
    Column("cantonese", UnicodeText),
    Column("grade", Integer),
    )

engine = create_engine("sqlite:///unihan.db",
                       echo=False, strategy='threadlocal')
session = sessionmaker(bind=engine,
                       autoflush=False,
                       transactional=True)
metadata.create_all(engine)

s = session()

# Convert codes to accents
##########################################################################
# code from Donald Chai

accenttable = {
    'a' : [u'a', u'ā', u'á', u'ǎ', u'à', u'a'],
    'e' : [u'e', u'ē', u'é', u'ě', u'è', u'e'],
    'i' : [u'i', u'ī', u'í', u'ǐ', u'ì', u'i'],
    'o' : [u'o', u'ō', u'ó', u'ǒ', u'ò', u'o'],
    'u' : [u'u', u'ū', u'ú', u'ǔ', u'ù', u'u'],
    'v' : [u'ü', u'ǖ', u'ǘ', u'ǚ', u'ǜ', u'ü'],
}
def convert(word):
    '''Converts a pinyin word to unicode'''
    word = word.lower()
    orig = word
    # convert ü to v for now to make life easier
    word = re.sub(u'\xfc|\xc3\xbc', 'v', word)
    # extract fields
    mo = re.match('([qwrtypsdfghjklzxcbnm]*)([aeiouv]*)(\D*)(\d?)', word)
    init  = mo.group(1)
    vowel = mo.group(2)
    final = mo.group(3)
    tone  = mo.group(4)
    # do nothing if no vowel or tone
    if vowel=='' or tone=='':
        return orig
    tone  = int(tone)
    if len(vowel)==1:
        vowel = accenttable[vowel][tone]
    elif vowel[-2]=='i' or vowel[-2]=='u':
        # put over last
        vowel = vowel[:-1] + accenttable[vowel[-1]][tone]
    else:
        # put over second to last
        vowel = vowel[:-2] + accenttable[vowel[-2]][tone] + vowel[-1]
    return init+vowel+final

##########################################################################

kanji = {}
import codecs
for line in codecs.open("Unihan.txt", encoding="utf-8"):
    try:
        (u, f, v) = line.strip().split("\t")
    except ValueError:
        continue
    if not u.startswith("U+"):
        continue
    n = int(u.replace("U+",""), 16)
    if not n in kanji:
        kanji[n] = {}
    if f == "kMandarin":
        kanji[n]['mandarin'] = " ".join([convert(w) for w in v.split()])
    elif f == "kCantonese":
        kanji[n]['cantonese'] = v
    elif f == "kGradeLevel":
        kanji[n]['grade'] = int(v)

dict = [{'id': k,
         'mandarin': v.get('mandarin'),
         'cantonese': v.get('cantonese'),
         'grade': v.get('grade') } for (k,v) in kanji.items()
        if v.get('mandarin') or v.get('cantonese') or v.get('grade')]
s.execute(unihanTable.insert(), dict)

s.commit()
