#!/bin/env node
/* eslint-disable no-console */
/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

"use strict";

const path = require("path");
const fs = require("fs");
const webpack = require("webpack");
const webpackConfig = require("./webpack.config");
const {spawn, checkCommand, hermesBinaryWithPath} = require("./osutils.js");

const outputDir = path.resolve(__dirname, "build/dist");

// Look for hermesc
const hermescPath = hermesBinaryWithPath("hermesc");
checkCommand(hermescPath);

fs.mkdirSync(outputDir, {recursive: true});

let wConfig = webpackConfig({production: true});
new Promise((resolve, reject) =>
{
  webpack(wConfig, (err, stats) =>
  {
    if (err || stats.hasErrors())

      return reject(err || stats.compilation.errors);

    // Done processing
    resolve(stats);
  });
}).then(stats =>
{
  /** @type {Map<string, webpack.Entrypoint>} */
  let entrypoints = stats.compilation.entrypoints;

  // there is probably a more fancy way
  // of filling `Promise.all` with generators etc
  let processes = [];
  for (let entrypoint of entrypoints.keys())
  {
    processes.push(
      spawn(hermescPath, [
        "-emit-binary",
        "-out=" + path.join(outputDir, entrypoint + ".hbc"),
        path.join("build", "webpack", entrypoint + ".js")
      ])
    );
  }
  return Promise.all(processes);
})
.then(_ => console.log("Done building binary hbc"))
.catch(reason =>
{
  console.error(reason);
});
