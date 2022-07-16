#Berkeley CS61B project 2 GITLET

<p>
  This project is a miniature git written in java.
</p>
<br />
<p>
  This project came with testing infrastructure, including make file and 3 test files in /testing/samples + 
some utility classes for de/serializing to files and reading and writting files.
  I've added a bunch of tests of my own for the commands that have given me more trouble in the same dir.
</p>
<br />
<p>
  The work I did is what you will find in /gitlet/ respository, main and commit java files.
</p>
<br />
<ol><strong>How to run:</strong>
  <li>make</li>
  <li><ol>java gitlet.Main [command]:
    <li>init</li>
    <li>add [filename]</li>
    <li>commit [message]</li>
    <li>rm [fileName]</li>
    <li>find [log message]</li>
    <li>log</li>
    <li>global-log</li>
    <li>status</li>
    <li>checkout [fileName]</li>
    <li>checkout [commitID, filename]</li>
    <li>checkout [branchName]</li>
    <li>branch</li>
    <li>merge</li>
    </ol>
    </li>
</ol>
