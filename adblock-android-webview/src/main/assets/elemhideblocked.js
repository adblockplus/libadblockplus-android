/*
The script is called for every blocked element that should be element hidden, eg. images.
Excluding redefinition of the functions here for performance reason
(assuming functions code is not changed between invocations).
*/
if (typeof(hideElement) !== typeof(Function))
{
  function hideElement(element)
  {
    function doHide()
    {
      let propertyName = "display";
      let propertyValue = "none";
      if (element.localName == "frame")
      {
        propertyName = "visibility";
        propertyValue = "hidden";
      }

      if (element.style.getPropertyValue(propertyName) != propertyValue ||
          element.style.getPropertyPriority(propertyName) != "important")
      {
        element.style.setProperty(propertyName, propertyValue, "important");
      }
    }

    doHide();

    new MutationObserver(doHide).observe(element,
      {
        attributes: true,
        attributeFilter: ["style"]
      });
  }

  function elemhideForSelector(url, selector, attempt)
  {
    if (attempt == 50) // time-out = 50 attempts with 100 ms delay = 5 seconds
    {
      {{DEBUG}} console.log("Too many attempts for selector " + selector + " with url " + url + ", exiting");
      return;
    }

    let elements = document.querySelectorAll(selector);

    // for some reason it can happen that no elements are found by selectors (DOM not ready?)
    // so the idea is to retry with some delay
    if (elements.length > 0)
    {
      for (let element of elements)
      {
        if (element.src == url)
        {
          hideElement(element);
        }
      }
    }
    else
    {
      {{DEBUG}} console.log("Nothing found for selector " + selector + ", retrying elemhide in 100 millis");
      setTimeout(elemhideForSelector, 100, url, selector, attempt + 1);
    }
  }
}