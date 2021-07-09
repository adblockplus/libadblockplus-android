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
const webpack = require("webpack");

const ignoredRequirements = [];

/**
 * Returns webpack configuration
 *
 * @param {*} env current environment
 * @param {string[]} _ command line args
 * @returns {Configuration}
 */
module.exports = function(env, _)
{
  return {
    mode: env.production ? "production" : "development",
    devtool: env.production ? "source-map" : "inline-source-map",
    entry: {
      // capitals because then use the entrypoint name in the name of the library
      API: {
        import: path.resolve(__dirname, "lib/api.js")
      },
      test: {
        import: path.resolve(__dirname, "test/index.js")
      }
    },
    output: {
      // `library` simply tells to expose all imports from the entry point
      // we actually don't need a library for `test` entry, but lest keep it
      // for the sake of simplicity
      library: "[name]",
      filename: "[name].js",
      path: path.resolve(__dirname, "build/webpack")
    },
    plugins: [
      new webpack.IgnorePlugin({
        checkResource(res)
        {
          return ignoredRequirements.includes(res);
        }
      })
    ],
    target: ["es5"],
    module: {
      rules: [
        {
          test: /\.js$/,
          exclude: /node_modules/,
          use: {
            loader: "babel-loader",
            options: {
              sourceType: "unambiguous",
              presets:  [
                "module:metro-react-native-babel-preset",
              ],
            }
          }
        }
      ]
    },
    resolve: {
      modules: [
        path.resolve(__dirname, "node_modules"),
        path.resolve(__dirname, "../extern/adblockpluscore/lib")
      ],
      // using `alias` required for core to function properly
      // core does not know where implementations of platform live
      // it just imports `require('io')` and it should be resolvable
      alias: {
        compat$: path.resolve(__dirname, "./lib/platform/compat.js"),
        info$: path.resolve(__dirname, "./lib/platform/info.js"),
        io$: path.resolve(__dirname, "./lib/platform/io.js"),
        prefs$: path.resolve(__dirname, "./lib/platform/prefs.js")
      }
    },
    experiments: {
      topLevelAwait: true
    }
  };
};
