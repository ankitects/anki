// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use libc::wchar_t;

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct PyStatus {
    pub _type: ::std::os::raw::c_uint,
    pub func: *const ::std::os::raw::c_char,
    pub err_msg: *const ::std::os::raw::c_char,
    pub exitcode: ::std::os::raw::c_int,
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct PyWideStringList {
    pub length: isize,
    pub items: *mut *mut wchar_t,
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct PyConfig {
    pub _config_init: ::std::os::raw::c_int,
    pub isolated: ::std::os::raw::c_int,
    pub use_environment: ::std::os::raw::c_int,
    pub dev_mode: ::std::os::raw::c_int,
    pub install_signal_handlers: ::std::os::raw::c_int,
    pub use_hash_seed: ::std::os::raw::c_int,
    pub hash_seed: ::std::os::raw::c_ulong,
    pub faulthandler: ::std::os::raw::c_int,
    pub tracemalloc: ::std::os::raw::c_int,
    pub perf_profiling: ::std::os::raw::c_int,
    pub import_time: ::std::os::raw::c_int,
    pub code_debug_ranges: ::std::os::raw::c_int,
    pub show_ref_count: ::std::os::raw::c_int,
    pub dump_refs: ::std::os::raw::c_int,
    pub dump_refs_file: *mut wchar_t,
    pub malloc_stats: ::std::os::raw::c_int,
    pub filesystem_encoding: *mut wchar_t,
    pub filesystem_errors: *mut wchar_t,
    pub pycache_prefix: *mut wchar_t,
    pub parse_argv: ::std::os::raw::c_int,
    pub orig_argv: PyWideStringList,
    pub argv: PyWideStringList,
    pub xoptions: PyWideStringList,
    pub warnoptions: PyWideStringList,
    pub site_import: ::std::os::raw::c_int,
    pub bytes_warning: ::std::os::raw::c_int,
    pub warn_default_encoding: ::std::os::raw::c_int,
    pub inspect: ::std::os::raw::c_int,
    pub interactive: ::std::os::raw::c_int,
    pub optimization_level: ::std::os::raw::c_int,
    pub parser_debug: ::std::os::raw::c_int,
    pub write_bytecode: ::std::os::raw::c_int,
    pub verbose: ::std::os::raw::c_int,
    pub quiet: ::std::os::raw::c_int,
    pub user_site_directory: ::std::os::raw::c_int,
    pub configure_c_stdio: ::std::os::raw::c_int,
    pub buffered_stdio: ::std::os::raw::c_int,
    pub stdio_encoding: *mut wchar_t,
    pub stdio_errors: *mut wchar_t,
    #[cfg(windows)]
    pub legacy_windows_stdio: ::std::os::raw::c_int,
    pub check_hash_pycs_mode: *mut wchar_t,
    pub use_frozen_modules: ::std::os::raw::c_int,
    pub safe_path: ::std::os::raw::c_int,
    pub int_max_str_digits: ::std::os::raw::c_int,
    pub cpu_count: ::std::os::raw::c_int,
    pub pathconfig_warnings: ::std::os::raw::c_int,
    pub program_name: *mut wchar_t,
    pub pythonpath_env: *mut wchar_t,
    pub home: *mut wchar_t,
    pub platlibdir: *mut wchar_t,
    pub module_search_paths_set: ::std::os::raw::c_int,
    pub module_search_paths: PyWideStringList,
    pub stdlib_dir: *mut wchar_t,
    pub executable: *mut wchar_t,
    pub base_executable: *mut wchar_t,
    pub prefix: *mut wchar_t,
    pub base_prefix: *mut wchar_t,
    pub exec_prefix: *mut wchar_t,
    pub base_exec_prefix: *mut wchar_t,
    pub skip_source_first_line: ::std::os::raw::c_int,
    pub run_command: *mut wchar_t,
    pub run_module: *mut wchar_t,
    pub run_filename: *mut wchar_t,
    pub sys_path_0: *mut wchar_t,
    pub _install_importlib: ::std::os::raw::c_int,
    pub _init_main: ::std::os::raw::c_int,
    pub _is_python_build: ::std::os::raw::c_int,
}
