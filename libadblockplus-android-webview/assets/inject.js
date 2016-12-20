var hideElements = function()
{
  // no need to invoke if already invoked on another event
  if ({{BRIDGE}}.isElementsHidden())
  {
    {{DEBUG}} console.log('already hidden, exiting');
    return;
  }

  // hide using element visibility (to be replaced with script body)
  {{HIDE}}

  {{BRIDGE}}.setElementsHidden(true); // set flag not to do it again
};

if ({{BRIDGE}}.getAddDomListener() && document.readyState != 'complete')
{
  {{BRIDGE}}.setAddDomListener(false);

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

};