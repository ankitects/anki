import os
import re
import sys

from anki.importing.noteimp import NoteImporter, ForeignNote
from anki.lang import _

'''Import from text file, fields begin at the start of a line, the
field value is comprised of every indented line that follows the
field.

This text import is especially nice when you want to compose your
cards in a standard text editor while preserving intuitive line
breaks that enhance readability.

Two predefined special fields are recognized:

* tags
* fields (defaults to [front, back]

Everything else is considered a card definition.

Fields always begin at the start of a line and include a colon.

Lines that begin with # are considered comments and are ignored.

Empty lines are ignored.

Here is a simple example from an aviation card deck related to instruments:

#------------------------------------------------------------------------------
tags:
  aviation
  instruments

front:
  What is magnetic compass acceleration/deceleration error?
back:
  <ul>
    <li>Occurs East/West, does not occur North/South</li>
    <li>Acceleration shows north turn.</li>
    <li>Deceleration shows south turn.</li>
  </ul>
  Memory aid<br>
  ANDS: Acclerate North, Decelerate South
#------------------------------------------------------------------------------

The beginning of the file defines tags assocated with the deck, in
this example the deck has the "avaiation" and "instruments" tags. The
"tags:" field is at the start of line, it's values are each indented
line underneath.

No fields are defined, instead the default fields of "front" and
"back" are assumed.

The deck has only 1 card with a front and back. The front field begins
at the start of a line, it's content is every indented line beneath
it. The back field likewise must be at the begining of a line. The
back is formatted with HTML and is comprised of the 7 indented lines
that follow.

When a note is missing a field a warning message will be emitted.
The warning can be silenced by prepending the field name with
an asserterix (*) which marks the field as being optional. The "Add Reverse"
field is a good example, only some notes will set this. Here is an
example, the first card has the optional "Add Reverse" the second does not.


#------------------------------------------------------------------------------
tags:
  aviation
  regulations

fields:
  front
  back
  *Add Reverse

front:
  Part 61
back:
  Certification for private pilots, flight instructors, and ground instructors.
Add Reverse:
  True

front:
  Required documents
back:
  <em>ARROW</em><br>
  <ul>
    <li>Airworthiness Certificate</li>
    <li>Registrations</li>
    <li>Radio license (outside USA)</li>
    <li>Operators manual</li>
    <li>Weight & Balance</li>
  </ul>
#------------------------------------------------------------------------------

'''

class IndentedTextImporter(NoteImporter):
    def __init__(self, *args):
        super(IndentedTextImporter, self).__init__(*args)
        self.parser = None

    def open(self):
        if self.parser is None:
            try:
                self.parser = IndentedTextParser(self.file, self.log)
                self.parser.parse()
                # Useful for debugging the parser
                #self.parser.dump()
            except Exception as e:
                self.log.append(str(e))

    def fields(self):
        self.open()
        return len(self.parser.field_names)

    def foreignNotes(self):
        self.open()
        if not self.parser.success:
            return []
        foreign_notes = []
        for note in self.parser.notes:
            foreign_note = ForeignNote()

            foreign_note.tags.extend(self.parser.tags)

            for field in self.parser.field_names:
                field_value = note.get(field, [])
                field_text = ''.join([x.strip() for x in field_value])
                foreign_note.fields.append(field_text)

            foreign_notes.append(foreign_note)

        return foreign_notes


class IndentedTextParser(object):
    comment_re = re.compile(r'^\s*#.*$')
    field_name_re = re.compile(r'^(\w+(\s+\w+)*):.*$')
    continuation_re = re.compile(r'^[ \t]+')
    empty_line_re = re.compile(r'^\s*$')

    def __init__(self, filename, log):
        self.success = True
        self.filename = filename
        self.file = None
        self.eof = False
        self.line_num = 0
        self.tags = []
        self.field_names = ['front', 'back']
        self.optional_field_names = []
        self.note = {}
        self.notes = []
        self.log = log

    def get_line(self):
        self.line = self.file.readline()
        self.line_num += 1
        if self.line == '':
            self.eof = True
        return self.line

    def is_continuation(self):
        if self.continuation_re.search(self.line):
            return True
        return False

    def is_comment(self):
        if self.comment_re.search(self.line):
            return True
        else:
            return False

    def is_empty_line(self):
        if self.empty_line_re.search(self.line):
            return True
        else:
            return False

    def right_trim(self, line):
        return line.rstrip()

    def validate_note(self):
        for field_name in self.field_names:
            if field_name not in self.note and \
               field_name not in self.optional_field_names:
                self.log.append('Missing field "%s" near line %d' %
                                (field_name, self.line_num))

    def emit_note(self):
        if self.note:
            self.validate_note()
            self.notes.append(self.note)
            self.note = {}

    def emit_field(self, field_name, field_value):
        if field_name is None:
            return

        if field_name == 'tags':
            self.tags = [x.strip() for x in field_value]
        elif field_name == 'fields':
            self.field_names = []
            self.optional_field_names = []
            for field_name in field_value:
                field_name = field_name.strip()
                if field_name.startswith("*"):
                    field_name = field_name[1:]
                    self.optional_field_names.append(field_name)
                self.field_names.append(field_name)
        else:
            if field_name not in self.field_names:
                self.success = False
                self.log.append("unknown field name near line %d: '%s'" %
                                (self.line_num, field_name))

            if field_name in self.note:
                self.emit_note()

            self.note[field_name] = field_value

    def parse(self):
        try:
            self.file = open(self.filename)
        except Exception as e:
            self.success = False
            self.log.append(str(e))
            return
        try:
            # Iterate over each line in file
            self.get_line()
            while not self.eof:
                if self.is_comment():
                    self.get_line()
                    continue

                # Found a field name?
                match = self.field_name_re.search(self.line)
                if match:
                    # Yes, new field name
                    field_name = match.group(1)
                    # Consume all continuation lines following field name
                    field_value = []
                    while True:
                        self.get_line()
                        if self.is_comment():
                            self.get_line()
                            continue
                        if self.is_continuation():
                            field_value.append(self.right_trim(self.line))
                        else:
                            self.emit_field(field_name, field_value)
                            break
                else:
                    # Not a field name, get next line
                    if not self.is_empty_line():
                        self.success = False
                        self.log.append("syntax error at line %d: '%s'" %
                                        (self.line_num,
                                         self.right_trim(self.line)))
                    self.get_line()
            self.emit_note()
        finally:
            self.file.close()

    def dump(self, logdir='/tmp'):
        '''Useful for debugging the parser

        Writes out the parsed data to a file.
        '''

        logfile = os.path.join(logdir, os.path.basename(self.filename))
        f = open(logfile, 'w')
        if self.tags:
            f.write('tags:\n')
            for tag in self.tags:
                f.write('  %s\n' % (tag))
            f.write('\n')

        f.write('fields:\n')
        for field_name in self.field_names:
            f.write('  %s\n' % (field_name))
        f.write('\n')

        for note in self.notes:
            for field_name in self.field_names:
                f.write('%s:\n' % field_name)
                field_value = note.get(field_name, [])
                for line in field_value:
                    f.write(line)
                    f.write('\n')
            f.write('\n')
