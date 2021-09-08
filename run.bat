set PYTHONWARNINGS=default
call .\bazel.bat run --explain=bazel-.log --subcommands --show_progress --worker_verbose --verbose_failures --verbose_explanations=true --test_output=streamed %BUILDARGS% //qt:runanki -k -- %*
