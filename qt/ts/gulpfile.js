/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */
var gulp = require("gulp");
var concat = require('gulp-concat');
var resolveDependencies = require('gulp-resolve-dependencies');

var ts = require("gulp-typescript");
var tsProject = ts.createProject("tsconfig.json");

gulp.task("reviewer", function() {
  return gulp
    .src(["src/reviewer.ts"])
    .pipe(resolveDependencies({
      pattern: /^\s*\/\/\/\s*<\s*reference\s*path\s*=\s*(?:"|')([^'"\n]+)/gm
    }))
    .on('error', function(err) {
        console.log(err.message);
    })
    .pipe(tsProject())
    .pipe(concat('reviewer.js'))
    .pipe(gulp.dest("../aqt_data/web/"));
});
