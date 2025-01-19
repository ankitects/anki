# The code in this file is licensed under the BSD 3 terms and conditions.

import os.path
import re



# Get path to input file (CONTRIBUTORS).
input_file  = "../CONTRIBUTORS"
# Get path to output file (about_contributors.py).
output_file = "../qt/aqt/about_contributors.py"
# Template file which is used to create the `allusers` function in about.py
tmp_allusers_template = """#This list has been generated from CONTRIBUTORS file.
allusers_gen = sorted(
	(
{{CONTENT}}
	)
)
"""



# This uses ANSI escape sequences to color messages
class Colors:
	INFO    = '\033[94m' # blue
	WARNING = '\033[93m' # yellow
	ERROR   = '\033[91m' # red
	RESET   = '\033[0m'  # reset formatting



# convenience functions
def print_info(*args):
	print("{0}INFO{1}:\t{2}".format(Colors.INFO, Colors.RESET, *args))

def print_error(*args):
	print("{0}ERROR{1}:\t{2}".format(Colors.ERROR, Colors.RESET, *args))



# The CONTRIBUTORS file has some text at the top, followed by the string
# "********************", followed by the names of contributors, followed by
# another "********************" and finally some text at the bottom.
# Only the names of the contributors is relevant here. That's why this code
# will:
#     1. Find the first and last occurrence of "********************" and store
#        them in a variable.
#     2. Remove every line from first line to first occurrence of that string.
#     3. Remove every line from last line to second occurrence of that string.
#     4. Remove the <some@mail.com> part for every contributor, if present.
#     5. Format the names so that about.py (about dialog) can use them.
#     6. Write the file to disk.

# check if file exists
if os.path.isfile(input_file):
	# holds the first and second occurrence of "********************"
	first_and_second_occurrence = [0,0]
	# if True, the script found a second occurrence of
	# "********************" instead of the first.
	did_find_first = False
	
	#print_info("Found input file: `{0}'".format(input_file))
	
	# open file and read lines
	with open(input_file, "r") as in_file:
		lines = in_file.readlines()
		
		for line_number, line in enumerate(lines):
			#print_info("Line {0} – `{1}'".format(line_number,
			#				     line))
			
			# look for the string "********************". Line
			# break needs to be removed so that it doesn't
			# interfere with matching.
			if line.strip("\n") == "********************":
				#print_info("String occurrence in line: {0}"
				#	   "".format(line_number))
				if not did_find_first:
					first_and_second_occurrence[
						0] = line_number
					did_find_first = True
				else:
					first_and_second_occurrence[
						1] = line_number
		
		#print_info("First, Second occurrence: {0}".format(
		#	first_and_second_occurrence))
	
	# store the lines with the contributor names
	contributor_lines = lines[first_and_second_occurrence[0] + 1:
				  first_and_second_occurrence[1] + 0]
	#print_info(contributor_lines)
	
	# remove the part between "<*>". This often is an email, but that isn't
	# always the case.
	contributor_lines_without_email = []
	for contributor in contributor_lines:
		# remove empty lines
		if contributor == "\n":
			continue
		
		# some contributors didn't add an email or similar next to their
		# name. This needs to be managed here.
		if re.search('<.+>', contributor):
			# contributor has email
			contributor_lines_without_email.append(
				"\t\t\"" + re.sub(r'\s<.+>', "\",",
						  contributor))
		else:
			# contributor does not have email
			contributor_lines_without_email.append(
				"\t\t\"" + re.sub(r'\n$', "\",\n",
						  contributor))
		
	#print_info(contributor_lines_without_email)
	
	
	
	with open(output_file, "w") as out_file:
		formatted_allusers = tmp_allusers_template.replace("{{"
								   "CONTENT}}",
								   "".join(contributor_lines_without_email))
		out_file.write(formatted_allusers)
else:
	print_error("Expected `{0}' but file doesn't exist.".format(input_file))


print_info("Generate contributors list: DONE.")
