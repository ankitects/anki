//
//  main.c
//  A main module for starting Python projects on Linux.
// Copied from briefcase-linux-system-template with changes to use the embedded
// Python build.

#include <stdio.h>
#include <libgen.h>
#include <limits.h>
#include <wchar.h>
#include <Python.h>

#include "pyversion.h"

// A global indicator of the debug level
char *debug_mode;

void debug_log(const char *format, ...);

int main(int argc, char *argv[]) {
    int ret = 0;
    PyStatus status;
    PyPreConfig preconfig;
    PyConfig config;
    char *app_module_name;
    char *path;
    char *exe_path;
    char *install_path;
    wchar_t *wtmp_str;
    wchar_t *app_packages_path_str;
    PyObject *app_packages_path;
    PyObject *app_module;
    PyObject *module;
    PyObject *module_attr;
    PyObject *method_args;
    PyObject *result;
    PyObject *exc_type;
    PyObject *exc_value;
    PyObject *exc_traceback;
    PyObject *systemExit_code;

    // Set the global debug state based on the runtime environment
    debug_mode = getenv("BRIEFCASE_DEBUG");

    // Establish where the executable is located;
    // other application paths will be computed relative to this location
    exe_path = realpath("/proc/self/exe", NULL);
    install_path = dirname(strdup(exe_path));
    debug_log("Install path: %s\n", install_path);

    // Generate an isolated Python configuration.
    PyPreConfig_InitIsolatedConfig(&preconfig);
    PyConfig_InitIsolatedConfig(&config);

    // Configure the Python interpreter:
    // Enforce UTF-8 encoding for stderr, stdout, file-system encoding and locale.
    // See https://docs.python.org/3/library/os.html#python-utf-8-mode.
    preconfig.utf8_mode = 1;
    // Ensure the locale is set (isolated interpreters won't by default)
    preconfig.configure_locale = 1;
    // Don't buffer stdio. We want output to appears in the log immediately
    config.buffered_stdio = 0;
    // Don't write bytecode; we can't modify the app bundle
    // after it has been signed.
    config.write_bytecode = 0;
    // Isolated apps need to set the full PYTHONPATH manually.
    config.module_search_paths_set = 1;
    // We're using the system Python's stdlib; however, we don't want anything
    // except for the core of the stdlib. If we import the site module, any
    // local site modifications (e.g., an active virtual environment) will leak
    // into the running app's sys.path.
    config.site_import = 0;

    debug_log("Pre-initializing Python runtime...\n");
    status = Py_PreInitialize(&preconfig);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to pre-initialize Python interpreter: %s", status.err_msg, nil]);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }

    // Set the executable name to match the actual executable
    debug_log("config.executable: %s\n", exe_path);
    status = PyConfig_SetBytesString(&config, &config.executable, exe_path);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to set executable name: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }

    // Determine the app module name. Look for the BRIEFCASE_MAIN_MODULE
    // environment variable first; if that exists, we're probably in test
    // mode. If it doesn't exist, fall back to the MainModule key in the
    // main bundle.
    app_module_name = getenv("BRIEFCASE_MAIN_MODULE");
    if (app_module_name == NULL) {
        app_module_name = "{{ cookiecutter.module_name }}";
    }
    debug_log("config.run_module: %s\n", app_module_name);
    status = PyConfig_SetBytesString(&config, &config.run_module, app_module_name);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to set app module name: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }

    // Point Python at the embedded standalone interpreter shipped inside the
    // app bundle. This decouples the package from the host distro's Python.
    path = malloc(PATH_MAX);
    snprintf(path, PATH_MAX, "%s/python", install_path);
    debug_log("config.home: %s\n", path);
    status = PyConfig_SetBytesString(&config, &config.home, path);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to set Python home: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }

    // Read the site config
    status = PyConfig_Read(&config);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to read site config: %s", status.err_msg]);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }

    // Set the full module path. This includes the stdlib, site-packages, and app code.
    debug_log("PYTHONPATH:\n");

    // Embedded python-build-standalone install layout:
    //   <install_path>/python/lib/python<TAG>.zip
    //   <install_path>/python/lib/python<TAG>
    //   <install_path>/python/lib/python<TAG>/lib-dynload
    snprintf(path, PATH_MAX, "%s/python/lib/python" PY_TAG ".zip", install_path);
    debug_log("- %s\n", path);
    wtmp_str = Py_DecodeLocale(path, NULL);
    status = PyWideStringList_Append(&config.module_search_paths, wtmp_str);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to set zipped form of stdlib path: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }
    PyMem_RawFree(wtmp_str);

    snprintf(path, PATH_MAX, "%s/python/lib/python" PY_TAG, install_path);
    debug_log("- %s\n", path);
    wtmp_str = Py_DecodeLocale(path, NULL);
    status = PyWideStringList_Append(&config.module_search_paths, wtmp_str);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to set unpacked form of stdlib path: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }
    PyMem_RawFree(wtmp_str);

    snprintf(path, PATH_MAX, "%s/python/lib/python" PY_TAG "/lib-dynload", install_path);
    debug_log("- %s\n", path);
    wtmp_str = Py_DecodeLocale(path, NULL);
    status = PyWideStringList_Append(&config.module_search_paths, wtmp_str);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to set stdlib binary module path: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }
    PyMem_RawFree(wtmp_str);

    // Add the app path
    strcpy(path, install_path);
    strcat(path, "/app");
    debug_log("- %s\n", path);
    wtmp_str = Py_DecodeLocale(path, NULL);
    status = PyWideStringList_Append(&config.module_search_paths, wtmp_str);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to set app path: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }
    PyMem_RawFree(wtmp_str);

    debug_log("Configure argc/argv...\n");
    status = PyConfig_SetBytesArgv(&config, argc, argv);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to configured argc/argv: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }

    debug_log("Initializing Python runtime...\n");
    status = Py_InitializeFromConfig(&config);
    if (PyStatus_Exception(status)) {
        // crash_dialog("Unable to initialize Python interpreter: %s", status.err_msg);
        PyConfig_Clear(&config);
        Py_ExitStatusException(status);
    }


    // Adding the app_packages as site directory.
    //
    // This adds app_packages to sys.path and executes any .pth
    // files in that directory.
    strcpy(path, install_path);
    strcat(path, "/app_packages");
    app_packages_path_str = Py_DecodeLocale(path, NULL);
    free(path);

    debug_log("Adding app_packages as site directory: %S\n", app_packages_path_str);

    module = PyImport_ImportModule("site");
    if (module == NULL) {
        // crash_dialog("Could not import site module");
        exit(-11);
    }

    module_attr = PyObject_GetAttrString(module, "addsitedir");
    if (module_attr == NULL || !PyCallable_Check(module_attr)) {
        // crash_dialog("Could not access site.addsitedir");
        exit(-12);
    }

    app_packages_path = PyUnicode_FromWideChar(app_packages_path_str, wcslen(app_packages_path_str));
    if (app_packages_path == NULL) {
        //crash_dialog("Could not convert app_packages path to unicode");
        exit(-13);
    }
    PyMem_RawFree(app_packages_path_str);

    method_args = Py_BuildValue("(O)", app_packages_path);
    if (method_args == NULL) {
        // crash_dialog("Could not create arguments for site.addsitedir");
        exit(-14);
    }

    result = PyObject_CallObject(module_attr, method_args);
    if (result == NULL) {
        // crash_dialog("Could not add app_packages directory using site.addsitedir");
        exit(-15);
    }


    // Start the app module.
    //
    // From here to Py_ObjectCall(runmodule...) is effectively
    // a copy of Py_RunMain() (and, more  specifically, the
    // pymain_run_module() method); we need to re-implement it
    // because we need to be able to inspect the error state of
    // the interpreter, not just the return code of the module.
    debug_log("Running app module: %s\n", app_module_name);
    module = PyImport_ImportModule("runpy");
    if (module == NULL) {
        // crash_dialog(@"Could not import runpy module");
        exit(-2);
    }

    module_attr = PyObject_GetAttrString(module, "_run_module_as_main");
    if (module_attr == NULL) {
        // crash_dialog(@"Could not access runpy._run_module_as_main");
        exit(-3);
    }

    app_module = PyUnicode_FromString(app_module_name);
    if (app_module == NULL) {
        // crash_dialog(@"Could not convert module name to unicode");
        exit(-3);
    }

    method_args = Py_BuildValue("(Oi)", app_module, 0);
    if (method_args == NULL) {
        // crash_dialog(@"Could not create arguments for runpy._run_module_as_main");
        exit(-4);
    }

    // Print a separator to differentiate Python startup logs from app logs,
    // then flush stdout/stderr to ensure all startup logs have been output.
    debug_log("---------------------------------------------------------------------------\n");
    fflush(stdout);
    fflush(stderr);

    // Invoke the app module
    result = PyObject_Call(module_attr, method_args, NULL);

    if (result == NULL) {
        // Retrieve the current error state of the interpreter.
        PyErr_Fetch(&exc_type, &exc_value, &exc_traceback);
        PyErr_NormalizeException(&exc_type, &exc_value, &exc_traceback);

        if (exc_traceback == NULL) {
            // crash_dialog(@"Could not retrieve traceback");
            exit(-5);
        }

        if (PyErr_GivenExceptionMatches(exc_value, PyExc_SystemExit)) {
            systemExit_code = PyObject_GetAttrString(exc_value, "code");
            if (systemExit_code == NULL) {
                debug_log("Could not determine exit code\n");
                ret = -10;
            } else if (systemExit_code == Py_None) {
                // SystemExit with a code of None; documented as a return code
                // of 0.
                ret = 0;
            } else if (PyLong_Check(systemExit_code)) {
                // SystemExit with error code
                ret = (int) PyLong_AsLong(systemExit_code);
            } else {
                // SystemExit with a string for an error code.
                ret = 1;
            }
        } else {
            // Non-SystemExit; likely an uncaught exception
            printf("---------------------------------------------------------------------------\n");
            printf("Application quit abnormally!\n");
            ret = -6;
        }

        if (ret != 0) {
            // Restore the error state of the interpreter, and print exception
            // to stderr. In the case of a SystemExit, this will exit with a
            // status of 1.
            PyErr_Restore(exc_type, exc_value, exc_traceback);
            PyErr_Print();
        }
    }

    Py_Finalize();

    return ret;
}

void debug_log(const char *format, ...) {
    if (debug_mode) {
        va_list args;
        va_start(args, format);
        vprintf(format, args);
        va_end(args);
    }
}
