========
Pystache
========

Inspired by ctemplate_ and et_, Mustache_ is a
framework-agnostic way to render logic-free views.

As ctemplates says, "It emphasizes separating logic from presentation:
it is impossible to embed application logic in this template language."

Pystache is a Python implementation of Mustache. Pystache requires
Python 2.6.

Documentation
=============

The different Mustache tags are documented at `mustache(5)`_.

Install It
==========

::

    pip install pystache


Use It
======

::

    >>> import pystache
    >>> pystache.render('Hi {{person}}!', {'person': 'Mom'})
    'Hi Mom!'

You can also create dedicated view classes to hold your view logic.

Here's your simple.py::

    import pystache
    class Simple(pystache.View):
        def thing(self):
            return "pizza"

Then your template, simple.mustache::

    Hi {{thing}}!

Pull it together::

    >>> Simple().render()
    'Hi pizza!'


Test It
=======

nose_ works great! ::

    pip install nose
    cd pystache
    nosetests


Author
======

::

    context = { 'author': 'Chris Wanstrath', 'email': 'chris@ozmm.org' }
    pystache.render("{{author}} :: {{email}}", context)


.. _ctemplate: http://code.google.com/p/google-ctemplate/
.. _et: http://www.ivan.fomichev.name/2008/05/erlang-template-engine-prototype.html
.. _Mustache: http://defunkt.github.com/mustache/
.. _mustache(5): http://defunkt.github.com/mustache/mustache.5.html
.. _nose: http://somethingaboutorange.com/mrl/projects/nose/0.11.1/testing.html