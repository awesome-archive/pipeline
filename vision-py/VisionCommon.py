__author__ = 'rodneykinney'

import sys, getopt, os

def parseArgs(usage):
    if (len(sys.argv) == 1):
        usage()
    inputDir = ''
    outputDir = ''
    try:
        opts, args = getopt.getopt(sys.argv[1:],"hi:o:",["inputDir=","outputDir="])
    except getopt.GetoptError:
        usage()
    for opt, arg in opts:
        if opt == '-h':
            usage()
        elif opt in ("-i", "--inputDir"):
            inputDir = arg
        elif opt in ("-o", "--outputDir"):
            outputDir = arg

    try:
        os.mkdir(outputDir)
    except:
        ''

    return inputDir, outputDir

def writeDirectory(inputDir, outputDir, content):
    for inFile in os.listdir(inputDir):
        outFile = os.path.join(outputDir, inFile)
    w = open(outFile,'w')
    w.write(content())
    w.close()





