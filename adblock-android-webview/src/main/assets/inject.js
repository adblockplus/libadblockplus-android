console.log("injected JS started")
var hideElements = function()
{
  // no need to invoke if already invoked on another event
  if (document.{{HIDDEN_FLAG}} === true)
  {
    {{DEBUG}} console.log('already hidden, exiting');
    return;
  }

  {{DEBUG}} console.log("Not yet hidden!")

  // hide using element visibility (to be replaced with script body)
  {{HIDE}}

  document.{{HIDDEN_FLAG}} = true; // set flag not to do it again
};

if (document.readyState === "complete")
{
  {{DEBUG}} console.log('document is in "complete" state, apply hiding')
  hideElements();
}
else
{
  {{DEBUG}} console.log('installing listener')

  // onreadystatechange event
  document.onreadystatechange = function()
  {
    {{DEBUG}} console.log('onreadystatechange() event fired (' + document.readyState + ')')
    if (document.readyState == 'interactive')
    {
      hideElements();
    }
  }

   // load event
  window.addEventListener('load', function(event)
  {
    {{DEBUG}} console.log('load() event fired');
    hideElements();
  });

  // DOMContentLoaded event
  document.addEventListener('DOMContentLoaded', function()
  {
    {{DEBUG}} console.log('DOMContentLoaded() event fired');
    hideElements();
  }, false);
}
console.log("injected JS finished");

