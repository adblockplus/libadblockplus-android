"use strict";

const os = require("os");
const fs = require("fs");
const path = require("path");
const cp = require("child_process");

module.exports.checkCommand = function checkCommand(command)
{
  try
  {
    fs.accessSync(command, fs.constants.F_OK | fs.constants.X_OK);
  }
  catch (err)
  {
    console.error(command + " doesn't exists or is not executable");
    console.error(err);
    process.exit(1);
  }
};

module.exports.spawn = function(cmd, args)
{
  return new Promise((resolve, reject) =>
  {
    const p = cp.spawn(cmd, args, {stdio: ["ignore", "ignore", "inherit"]});
    p.on("close", code =>
    {
      if (code != 0)
        reject(code);
      else
        resolve(code);
    });
  });
};

// Depending on running for Host or Android,
// arch names may be different, so pay attention to this
function getArch()
{
  let arch = os.arch();
  let type = os.type();
  if (arch === "x64" && type === "Darwin")
    return "x86_64";

  if (arch === "x64")
    return "amd64";

  return arch;
}

// There is inconsistency between what Node returns as `os.type()`
// and what Java returns.
//
// On Windows 10, Java returns "Windows 10" for `os.name`,
//  Node returns "Windows_NT" for `os.type`
// On Mac Osx, Java return "Mac OsX", Node return "Darwin"
function osTypeToHermesOsType()
{
  let type = os.type();
  switch (type)
  {
    case "Windows_NT":
      return "win";
    case "Darwin":
      return "osx";
    default:
      return type.toLowerCase();
  }
}

/**
 * Formats a full path to binary also considering platform
 *
 * @param {string} binaryName
 * @returns {string} a binary with a full path prepended
 */
// For Windows Node returns "Windows_NT" for `os.type`
module.exports.hermesBinaryWithPath = function(binaryName)
{
  if (os.type() == "Windows_NT")
    binaryName = `Release\\${binaryName}.exe`;

  return path.resolve(
    __dirname,
    path.join("..", "hermes-libs", "build", "cmake", "hermes", "release",
              osTypeToHermesOsType(), getArch(), "bin", binaryName)
  );
};
