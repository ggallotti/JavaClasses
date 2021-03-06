
package com.genexus.internet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

class RFC822Reader implements MailReader
{
	private BufferedReader reader;
	private String separator;
	private String terminator;
	private String lastLine;
	private String readLine = "";
	private int    lastChar = 1;
	private boolean returnLF = false;
	private ArrayList separators;
	private ArrayList terminators;

	public RFC822Reader(BufferedReader reader)
	{
		this.reader = reader;
		this.separators = new ArrayList();
			this.terminators = new ArrayList();
	}

	public String getSeparator()
	{
		return separator;
	}

	public void setSeparator(String separator)
	{
		if	(separator == null)
		{
			this.separator = this.terminator = null;
		}		
		else
		{
			this.separator  = separator;
			this.terminator = separator + "--";
			if (!separators.contains(this.separator))
					separators.add(this.separator);
				if (!terminators.contains(this.terminator))
					terminators.add(this.terminator);
		}
	}

	public int read() throws IOException
	{
		if	(returnLF)
		{
			returnLF = false;
			return GXInternetConstants.LF;
		}

		if	(readLine == null)
		{
			readLine = "";
			lastChar = 1;
			return -1;
		}

		if	(lastChar >= readLine.length())
		{
			boolean returnCR = (readLine != null && lastChar >= readLine.length());

			readLine  = readLine();
			lastChar  = 0;

			if	(returnCR)
			{
				returnLF = true;
				return GXInternetConstants.CR;
			}
		}

		if	(readLine == null)
			return -1;

		if	(readLine.length() == 0)
			return 20;
		else
			return readLine.charAt(lastChar++);
	}

	private String realReadLine() throws IOException
	{
		return reader.readLine();
	}

	public String readLine() throws IOException
	{
		String out;

		if	(lastLine == null)
		{
			out = realReadLine();
		}
		else
		{
			out = lastLine;
			lastLine = null;
		}
		
		if	(out != null)
		{
			if	( 
					separator != null && 
					(out.equals("") || /*out.startsWith(separator)*/ StartsWithSeparator(out))
				)
			{
				if	(StartsWithSeparator(out))
				{
					lastLine = null;
					return null;
				}

				lastLine = realReadLine();
				if	(lastLine != null)
				{
					if	(lastLine.startsWith(terminator) || terminators.contains(lastLine))
					{
						setSeparator(null);
						lastLine = null;
						return null;
					} 
					else if	(lastLine.startsWith(separator) || separators.contains(lastLine))
					{
						lastLine = null;
						return null;
					}
				}				
			}
		}

		return out;
	}	

private boolean StartsWithSeparator(String str)
		{
			for (int i=0; i<separators.size(); i++)
			{
				if (str.startsWith((String)separators.get(i)))
					return true;
			}
			return false;
		}
		
        public void readUnreadedBytes() throws IOException
        {
            while (true)
            {
              if(reader.readLine() == null)
              {
                break;
              }
            }
        }
}
